package com.example.Contract_review.util;

import com.example.Contract_review.model.ReviewIssue;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于OpenXML纯XML修改的Word批注处理器
 *
 * 直接操作OOXML格式的XML文件，实现右侧批注功能：
 * - word/document.xml: 插入批注标记
 * - word/comments.xml: 创建/更新批注内容
 * - word/_rels/document.xml.rels: 管理文档关系
 */
@Component
public class WordXmlCommentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(WordXmlCommentProcessor.class);

    // OpenXML命名空间
    private static final Namespace W_NS = Namespace.get("w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main");
    private static final Namespace R_NS = Namespace.get("r", "http://schemas.openxmlformats.org/officeDocument/2006/relationships");

    // 批注ID计数器
    private final AtomicInteger commentIdCounter = new AtomicInteger(1);

    /**
     * 为Word文档添加批注（纯XML操作）
     *
     * @param docxBytes 原始DOCX文件字节数组
     * @param issues 审查问题列表
     * @param anchorStrategy 锚点策略
     * @param cleanupAnchors 是否清理锚点
     * @return 带批注的DOCX文件字节数组
     * @throws Exception 处理失败
     */
    public byte[] addCommentsToDocx(byte[] docxBytes, List<ReviewIssue> issues,
                                   String anchorStrategy, boolean cleanupAnchors) throws Exception {

        logger.info("开始XML方式添加批注：issues数量={}, 策略={}, 清理锚点={}",
                   issues.size(), anchorStrategy, cleanupAnchors);

        // 打开DOCX包
        try (InputStream docxStream = new ByteArrayInputStream(docxBytes);
             OPCPackage opcPackage = OPCPackage.open(docxStream)) {

            // 处理document.xml
            Document documentXml = loadDocumentXml(opcPackage);

            // 处理comments.xml（如果不存在则创建）
            Document commentsXml = loadOrCreateCommentsXml(opcPackage);

            // 为每个问题添加批注
            int addedCount = 0;
            for (ReviewIssue issue : issues) {
                if (addCommentForIssue(documentXml, commentsXml, issue, anchorStrategy)) {
                    addedCount++;
                }
            }

            // 清理锚点（如果需要）
            if (cleanupAnchors) {
                cleanupAnchorsInDocument(documentXml);
                logger.info("已清理文档中的锚点标记");
            }

            // 保存修改后的XML
            saveDocumentXml(opcPackage, documentXml);
            saveCommentsXml(opcPackage, commentsXml);

            // 更新关系文件
            updateDocumentRels(opcPackage);

            // 输出修改后的DOCX
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            opcPackage.save(outputStream);

            logger.info("XML批注处理完成：成功添加{}个批注", addedCount);
            return outputStream.toByteArray();
        }
    }

    /**
     * 加载document.xml
     */
    private Document loadDocumentXml(OPCPackage opcPackage) throws Exception {
        PackagePartName documentPartName = PackagingURIHelper.createPartName("/word/document.xml");
        PackagePart documentPart = opcPackage.getPart(documentPartName);

        SAXReader reader = new SAXReader();
        return reader.read(documentPart.getInputStream());
    }

    /**
     * 加载或创建comments.xml
     */
    private Document loadOrCreateCommentsXml(OPCPackage opcPackage) throws Exception {
        PackagePartName commentsPartName = PackagingURIHelper.createPartName("/word/comments.xml");

        try {
            PackagePart commentsPart = opcPackage.getPart(commentsPartName);
            SAXReader reader = new SAXReader();
            return reader.read(commentsPart.getInputStream());
        } catch (Exception e) {
            logger.info("comments.xml不存在，创建新的comments.xml");
            return createNewCommentsXml();
        }
    }

    /**
     * 创建新的comments.xml文档
     */
    private Document createNewCommentsXml() {
        Document doc = DocumentHelper.createDocument();

        Element root = doc.addElement(QName.get("comments", W_NS));

        // 添加必要的命名空间声明
        root.addNamespace("w", W_NS.getURI());
        root.addNamespace("mc", "http://schemas.openxmlformats.org/markup-compatibility/2006");
        root.addNamespace("r", R_NS.getURI());
        root.addNamespace("w14", "http://schemas.microsoft.com/office/word/2010/wordml");
        root.addNamespace("w15", "http://schemas.microsoft.com/office/word/2012/wordml");
        root.addNamespace("w16cex", "http://schemas.microsoft.com/office/word/2018/wordml/cex");
        root.addNamespace("w16cid", "http://schemas.microsoft.com/office/word/2016/wordml/cid");
        root.addNamespace("w16", "http://schemas.microsoft.com/office/word/2018/wordml");
        root.addNamespace("w16sdtdh", "http://schemas.microsoft.com/office/word/2020/wordml/sdtdh");
        root.addNamespace("w16se", "http://schemas.microsoft.com/office/word/2015/wordml/symex");

        return doc;
    }

    /**
     * 为单个问题添加批注
     */
    private boolean addCommentForIssue(Document documentXml, Document commentsXml,
                                      ReviewIssue issue, String anchorStrategy) {
        try {
            // 1. 在document.xml中找到插入位置
            Element targetParagraph = findTargetParagraph(documentXml, issue, anchorStrategy);
            if (targetParagraph == null) {
                logger.warn("无法找到批注插入位置：clauseId={}, anchorId={}",
                           issue.getClauseId(), issue.getAnchorId());
                return false;
            }

            // 2. 生成批注ID
            int commentId = commentIdCounter.getAndIncrement();

            // 3. 在document.xml中插入批注标记
            insertCommentRangeInDocument(targetParagraph, commentId);

            // 4. 在comments.xml中添加批注内容
            addCommentToCommentsXml(commentsXml, commentId, issue);

            logger.debug("成功添加批注：commentId={}, clauseId={}", commentId, issue.getClauseId());
            return true;

        } catch (Exception e) {
            logger.error("添加批注失败：clauseId=" + issue.getClauseId(), e);
            return false;
        }
    }

    /**
     * 在document.xml中查找目标段落
     */
    private Element findTargetParagraph(Document documentXml, ReviewIssue issue, String anchorStrategy) {
        Element documentElement = documentXml.getRootElement();
        Element body = documentElement.element(QName.get("body", W_NS));

        List<Element> paragraphs = body.elements(QName.get("p", W_NS));

        // 根据策略查找段落
        if ("anchorOnly".equalsIgnoreCase(anchorStrategy)) {
            return findParagraphByAnchor(paragraphs, issue.getAnchorId());
        } else if ("textFallback".equalsIgnoreCase(anchorStrategy)) {
            // 优先锚点，然后文本匹配
            Element found = findParagraphByAnchor(paragraphs, issue.getAnchorId());
            if (found != null) return found;

            return findParagraphByTextMatch(paragraphs, issue.getClauseId());
        } else {
            // 默认：preferAnchor - 优先锚点，然后条款ID文本匹配
            Element found = findParagraphByAnchor(paragraphs, issue.getAnchorId());
            if (found != null) return found;

            return findParagraphByTextMatch(paragraphs, issue.getClauseId());
        }
    }

    /**
     * 通过锚点查找段落
     */
    private Element findParagraphByAnchor(List<Element> paragraphs, String anchorId) {
        if (anchorId == null) return null;

        for (Element para : paragraphs) {
            // 查找书签
            List<Element> bookmarkStarts = para.elements(QName.get("bookmarkStart", W_NS));
            for (Element bookmark : bookmarkStarts) {
                String name = bookmark.attributeValue(QName.get("name", W_NS));
                if (anchorId.equals(name)) {
                    logger.debug("通过锚点找到目标段落：anchorId={}", anchorId);
                    return para;
                }
            }
        }

        return null;
    }

    /**
     * 通过文本匹配查找段落
     */
    private Element findParagraphByTextMatch(List<Element> paragraphs, String clauseId) {
        if (clauseId == null) return null;

        // 从clauseId提取数字（如 c1 -> 1）
        String numStr = clauseId.replaceAll("[^0-9]", "");
        if (numStr.isEmpty()) return null;

        String[] patterns = {
            "第" + numStr + "条",
            "第" + convertToChineseNumber(Integer.parseInt(numStr)) + "条",
            numStr + ".",
            numStr + "、"
        };

        for (Element para : paragraphs) {
            String paraText = extractParagraphText(para);

            for (String pattern : patterns) {
                if (paraText.contains(pattern)) {
                    logger.debug("通过文本匹配找到目标段落：pattern={}, text={}", pattern, paraText);
                    return para;
                }
            }
        }

        // 如果没找到，返回第一个段落作为fallback
        if (!paragraphs.isEmpty()) {
            logger.debug("使用第一个段落作为fallback");
            return paragraphs.get(0);
        }

        return null;
    }

    /**
     * 提取段落的纯文本内容
     */
    private String extractParagraphText(Element paragraph) {
        StringBuilder text = new StringBuilder();
        extractTextFromElement(paragraph, text);
        return text.toString();
    }

    /**
     * 递归提取元素中的文本
     */
    private void extractTextFromElement(Element element, StringBuilder text) {
        if ("t".equals(element.getName()) && W_NS.equals(element.getNamespace())) {
            text.append(element.getText());
        }

        for (Element child : element.elements()) {
            extractTextFromElement(child, text);
        }
    }

    /**
     * 将阿拉伯数字转换为中文数字
     */
    private String convertToChineseNumber(int num) {
        String[] chineseNumbers = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
        if (num >= 1 && num <= 10) {
            return chineseNumbers[num];
        } else if (num > 10 && num < 100) {
            int ten = num / 10;
            int unit = num % 10;
            if (ten == 1) {
                return unit == 0 ? "十" : "十" + chineseNumbers[unit];
            } else {
                return chineseNumbers[ten] + "十" + (unit == 0 ? "" : chineseNumbers[unit]);
            }
        }
        return String.valueOf(num);
    }

    /**
     * 在document.xml中插入批注范围标记
     */
    private void insertCommentRangeInDocument(Element paragraph, int commentId) {
        // 在段落开始插入批注范围起始标记
        Element commentRangeStart = paragraph.addElement(QName.get("commentRangeStart", W_NS));
        commentRangeStart.addAttribute(QName.get("id", W_NS), String.valueOf(commentId));

        // 在段落结束插入批注范围结束标记
        Element commentRangeEnd = paragraph.addElement(QName.get("commentRangeEnd", W_NS));
        commentRangeEnd.addAttribute(QName.get("id", W_NS), String.valueOf(commentId));

        // 添加批注引用
        Element run = paragraph.addElement(QName.get("r", W_NS));
        Element commentReference = run.addElement(QName.get("commentReference", W_NS));
        commentReference.addAttribute(QName.get("id", W_NS), String.valueOf(commentId));

        logger.debug("在段落中插入批注标记：commentId={}", commentId);
    }

    /**
     * 在comments.xml中添加批注内容
     */
    private void addCommentToCommentsXml(Document commentsXml, int commentId, ReviewIssue issue) {
        Element commentsRoot = commentsXml.getRootElement();

        // 创建批注元素
        Element comment = commentsRoot.addElement(QName.get("comment", W_NS));
        comment.addAttribute(QName.get("id", W_NS), String.valueOf(commentId));
        comment.addAttribute(QName.get("author", W_NS), "AI Review Assistant");
        comment.addAttribute(QName.get("date", W_NS), new Date().toString());
        comment.addAttribute(QName.get("initials", W_NS), "AI");

        // 创建批注内容段落
        Element commentPara = comment.addElement(QName.get("p", W_NS));
        Element commentRun = commentPara.addElement(QName.get("r", W_NS));
        Element commentText = commentRun.addElement(QName.get("t", W_NS));

        // 格式化批注文本
        String formattedComment = formatCommentText(issue);
        commentText.setText(formattedComment);

        logger.debug("在comments.xml中添加批注内容：commentId={}, 内容长度={}",
                    commentId, formattedComment.length());
    }

    /**
     * 格式化批注文本
     */
    private String formatCommentText(ReviewIssue issue) {
        StringBuilder sb = new StringBuilder();

        // 风险等级标签
        String severityLabel = getSeverityLabel(issue.getSeverity());
        sb.append("[").append(severityLabel).append("] ");

        // 类别
        if (issue.getCategory() != null) {
            sb.append(issue.getCategory()).append("问题：\n");
        }

        // 发现的问题
        if (issue.getFinding() != null) {
            sb.append(issue.getFinding()).append("\n");
        }

        // 建议
        if (issue.getSuggestion() != null) {
            sb.append("建议：").append(issue.getSuggestion());
        }

        return sb.toString();
    }

    /**
     * 获取风险等级标签
     */
    private String getSeverityLabel(String severity) {
        if (severity == null) {
            return "未知风险";
        }

        switch (severity.toUpperCase()) {
            case "HIGH":
                return "高风险";
            case "MEDIUM":
                return "中风险";
            case "LOW":
                return "低风险";
            default:
                return severity;
        }
    }

    /**
     * 清理文档中的锚点标记
     */
    private void cleanupAnchorsInDocument(Document documentXml) {
        Element documentElement = documentXml.getRootElement();
        Element body = documentElement.element(QName.get("body", W_NS));

        List<Element> paragraphs = body.elements(QName.get("p", W_NS));

        for (Element para : paragraphs) {
            // 移除以"anc-"开头的书签
            List<Element> bookmarkStarts = para.elements(QName.get("bookmarkStart", W_NS));
            bookmarkStarts.removeIf(bookmark -> {
                String name = bookmark.attributeValue(QName.get("name", W_NS));
                return name != null && name.startsWith("anc-");
            });

            List<Element> bookmarkEnds = para.elements(QName.get("bookmarkEnd", W_NS));
            bookmarkEnds.clear(); // 清理所有书签结束标记
        }

        logger.debug("清理锚点完成");
    }

    /**
     * 保存document.xml
     */
    private void saveDocumentXml(OPCPackage opcPackage, Document documentXml) throws Exception {
        PackagePartName documentPartName = PackagingURIHelper.createPartName("/word/document.xml");
        PackagePart documentPart = opcPackage.getPart(documentPartName);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLWriter writer = new XMLWriter(outputStream);
        writer.write(documentXml);
        writer.close();

        // 清空现有内容并写入新内容
        try (var partStream = documentPart.getOutputStream()) {
            partStream.write(outputStream.toByteArray());
            partStream.flush();
        }

        logger.debug("document.xml保存完成，大小: {} 字节", outputStream.size());
    }

    /**
     * 保存comments.xml
     */
    private void saveCommentsXml(OPCPackage opcPackage, Document commentsXml) throws Exception {
        PackagePartName commentsPartName = PackagingURIHelper.createPartName("/word/comments.xml");

        PackagePart commentsPart;
        try {
            commentsPart = opcPackage.getPart(commentsPartName);
        } catch (Exception e) {
            // 如果comments.xml不存在，创建新的part
            commentsPart = opcPackage.createPart(commentsPartName,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml");
            logger.info("创建新的comments.xml文件");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLWriter writer = new XMLWriter(outputStream);
        writer.write(commentsXml);
        writer.close();

        // 清空现有内容并写入新内容
        try (var partStream = commentsPart.getOutputStream()) {
            partStream.write(outputStream.toByteArray());
            partStream.flush();
        }

        logger.debug("comments.xml保存完成，大小: {} 字节", outputStream.size());
    }

    /**
     * 更新document.xml.rels关系文件
     */
    private void updateDocumentRels(OPCPackage opcPackage) throws Exception {
        PackagePartName relsPartName = PackagingURIHelper.createPartName("/word/_rels/document.xml.rels");

        try {
            PackagePart relsPart = opcPackage.getPart(relsPartName);

            // 读取现有关系
            SAXReader reader = new SAXReader();
            Document relsDoc = reader.read(relsPart.getInputStream());

            // 检查是否已有comments关系
            Element relationships = relsDoc.getRootElement();
            boolean hasCommentsRel = false;

            for (Element rel : relationships.elements()) {
                String target = rel.attributeValue("Target");
                if ("comments.xml".equals(target)) {
                    hasCommentsRel = true;
                    break;
                }
            }

            // 如果没有comments关系，添加它
            if (!hasCommentsRel) {
                Element newRel = relationships.addElement("Relationship");
                newRel.addAttribute("Id", "rComments");
                newRel.addAttribute("Type",
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments");
                newRel.addAttribute("Target", "comments.xml");

                // 保存更新后的关系文件
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                XMLWriter writer = new XMLWriter(outputStream);
                writer.write(relsDoc);
                writer.close();

                // 清空现有内容并写入新内容
                try (var partStream = relsPart.getOutputStream()) {
                    partStream.write(outputStream.toByteArray());
                    partStream.flush();
                }

                logger.debug("添加comments关系到document.xml.rels，大小: {} 字节", outputStream.size());
            } else {
                logger.debug("comments关系已存在，跳过添加");
            }

        } catch (Exception e) {
            logger.warn("更新document.xml.rels失败：{}", e.getMessage());
            // 创建基本的关系文件
            createBasicDocumentRels(opcPackage);
        }
    }

    /**
     * 创建基本的document.xml.rels文件
     */
    private void createBasicDocumentRels(OPCPackage opcPackage) throws Exception {
        PackagePartName relsPartName = PackagingURIHelper.createPartName("/word/_rels/document.xml.rels");

        // 创建基本的关系XML
        Document relsDoc = DocumentHelper.createDocument();
        Element relationships = relsDoc.addElement("Relationships");
        relationships.addNamespace("", "http://schemas.openxmlformats.org/package/2006/relationships");

        // 添加comments关系
        Element commentsRel = relationships.addElement("Relationship");
        commentsRel.addAttribute("Id", "rComments");
        commentsRel.addAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments");
        commentsRel.addAttribute("Target", "comments.xml");

        // 创建新的关系文件部分
        PackagePart relsPart = opcPackage.createPart(relsPartName, "application/vnd.openxmlformats-package.relationships+xml");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLWriter writer = new XMLWriter(outputStream);
        writer.write(relsDoc);
        writer.close();

        try (var partStream = relsPart.getOutputStream()) {
            partStream.write(outputStream.toByteArray());
            partStream.flush();
        }

        logger.info("创建新的document.xml.rels文件，大小: {} 字节", outputStream.size());
    }
}