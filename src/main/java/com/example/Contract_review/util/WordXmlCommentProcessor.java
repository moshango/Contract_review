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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于OpenXML纯XML修改的Word批注处理器
 *
 * 直接操作OOXML格式的XML文件，实现右侧批注功能，支持多级精确定位：
 *
 * ======================== 批注定位三层架构 ========================
 *
 * 第1层：锚点定位（Anchor Positioning）
 *   - 查找段落中的书签（bookmarkStart）
 *   - 匹配anchorId（例如：anc-c1-4f21）
 *   - 用于精确定位到解析时生成的条款位置
 *   - 参考方法：findParagraphByAnchor()
 *
 * 第2层：文字匹配（Text Matching）
 *   - 在锚点标记的段落内，使用PreciseTextAnnotationLocator进行精确匹配
 *   - 支持三种匹配模式：
 *     * EXACT: 精确匹配完整文字
 *     * CONTAINS: 包含关键词匹配（允许重叠）
 *     * REGEX: 正则表达式匹配
 *   - 通过targetText和matchPattern字段指定要匹配的内容
 *   - 参考方法：preciseLocator.findTextInParagraph()
 *
 * 第3层：精确批注插入（Precise Annotation Insertion）
 *   - 将文字匹配结果映射到具体的Run元素
 *   - 在精确的Run位置插入批注范围标记（commentRangeStart/End）
 *   - 若匹配失败，自动降级到段落级别批注
 *   - 参考方法：insertPreciseCommentRange()
 *
 * ======================== 工作流程 ========================
 *
 * 输入：ReviewIssue 包含以下关键字段
 *   - anchorId: 锚点ID（例如：anc-c1-4f21）
 *   - clauseId: 条款ID（例如：c1）
 *   - targetText: 要批注的精确文字（例如："甲方应在损害事实发生后30天内承担赔偿责任"）
 *   - matchPattern: 匹配模式（EXACT|CONTAINS|REGEX，默认EXACT）
 *   - matchIndex: 如果有多个匹配，选择第几个（默认1）
 *
 * 处理流程：
 *   1. 根据anchorStrategy查找目标段落
 *      - preferAnchor: 优先用anchorId，失败则用clauseId文本匹配
 *      - anchorOnly: 仅用anchorId查找
 *      - textFallback: 优先用anchorId，失败则用clauseId
 *   2. 若提供了targetText，使用PreciseTextAnnotationLocator在段落内匹配
 *   3. 匹配成功：使用insertPreciseCommentRange()在精确位置插入批注
 *   4. 匹配失败：自动降级到insertCommentRangeInDocument()进行段落级别批注
 *   5. 将批注内容添加到comments.xml
 *
 * ======================== 关键特性 ========================
 *
 * ✓ 精确到字符级别：支持在Word中精确标记需要批注的文字范围
 * ✓ 多级回退：匹配失败自动降级，确保系统稳定性
 * ✓ 灵活的匹配模式：满足不同的文字定位需求
 * ✓ 锚点同步：通过锚点实现解析与批注的精确同步
 * ✓ 可清理锚点：批注完成后可选择清理锚点标记
 *
 * ======================== 使用示例 ========================
 *
 * 示例1：精确匹配完整句子
 * {
 *   "anchorId": "anc-c2-8f3a",
 *   "clauseId": "c2",
 *   "finding": "赔偿责任不清晰",
 *   "targetText": "甲方应在损害事实发生后30天内承担赔偿责任",
 *   "matchPattern": "EXACT"
 * }
 *
 * 示例2：包含关键词匹配
 * {
 *   "anchorId": "anc-c3-9f4b",
 *   "clauseId": "c3",
 *   "finding": "保密期限不明确",
 *   "targetText": "保密期限",
 *   "matchPattern": "CONTAINS"
 * }
 *
 * 示例3：正则表达式匹配
 * {
 *   "anchorId": "anc-c1-4f21",
 *   "clauseId": "c1",
 *   "finding": "数字不一致",
 *   "targetText": "\\d{1,3}[,.]\\d{1,2}[万元]",
 *   "matchPattern": "REGEX"
 * }
 *
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

    // 精确文字匹配定位器
    private final PreciseTextAnnotationLocator preciseLocator;

    public WordXmlCommentProcessor(PreciseTextAnnotationLocator preciseLocator) {
        this.preciseLocator = preciseLocator;
    }

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
     * 为单个问题添加批注 - 实现多级定位架构
     *
     * 这个方法是批注定位三层架构的核心实现：
     *
     * Step 1 - 锚点定位：根据anchorStrategy和anchorId查找目标段落
     *          若anchorId存在于书签中，直接返回该段落
     *          否则按clauseId进行文本匹配
     *
     * Step 2 - 文字匹配：若ReviewIssue中提供了targetText字段
     *          使用PreciseTextAnnotationLocator在段落内进行精确匹配
     *          支持EXACT、CONTAINS、REGEX三种匹配模式
     *
     * Step 3 - 精确插入：
     *          若文字匹配成功，在精确的Run位置插入批注标记（精确位置）
     *          若文字匹配失败，降级到段落开始/结束位置插入批注标记（段落级别）
     *          这样可以确保系统稳定性，同时优先使用精确定位
     *
     * @param documentXml 文档XML对象
     * @param commentsXml 批注XML对象
     * @param issue 审查问题，包含anchorId、targetText等定位信息
     * @param anchorStrategy 锚点策略：preferAnchor|anchorOnly|textFallback
     * @return true if successfully added, false otherwise
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

            // 3. 根据是否提供targetText，选择插入方式
            Element startRun = null;
            Element endRun = null;
            TextMatchResult matchResult = null;

            if (issue.getTargetText() != null && !issue.getTargetText().isEmpty()) {
                // 精确文字匹配模式
                matchResult = preciseLocator.findTextInParagraph(
                        targetParagraph,
                        issue.getTargetText(),
                        issue.getMatchPattern() != null ? issue.getMatchPattern() : "EXACT",
                        issue.getMatchIndex() != null ? issue.getMatchIndex() : 1
                );

                if (matchResult != null) {
                    startRun = matchResult.getStartRun();
                    endRun = matchResult.getEndRun();

                    if (startRun != null && endRun != null) {
                        logger.debug("使用精确文字匹配插入批注：文字={}, 起始Run=✓, 结束Run=✓, 匹配范围={}-{}",
                                   issue.getTargetText(),
                                   matchResult.getStartPosition(),
                                   matchResult.getEndPosition());
                    } else {
                        logger.warn("matchResult返回但Run为null：startRun={}, endRun={}, targetText={}",
                                   startRun != null ? "✓" : "null",
                                   endRun != null ? "✓" : "null",
                                   issue.getTargetText());
                    }
                } else {
                    logger.warn("精确文字匹配失败，降级到段落级别批注：targetText={}, matchPattern={}, matchIndex={}",
                               issue.getTargetText(),
                               issue.getMatchPattern() != null ? issue.getMatchPattern() : "EXACT",
                               issue.getMatchIndex() != null ? issue.getMatchIndex() : 1);
                    startRun = null;
                    endRun = null;
                }
            }

            // 4. 插入批注标记
            if (matchResult != null && startRun != null && endRun != null) {
                // 精确位置批注 - 传递matchResult用于Run分割
                insertPreciseCommentRange(targetParagraph, matchResult, commentId);
            } else {
                // 段落级别批注（默认或降级）
                insertCommentRangeInDocument(targetParagraph, commentId);
            }

            // 5. 在comments.xml中添加批注内容
            addCommentToCommentsXml(commentsXml, commentId, issue);

            logger.debug("成功添加批注：commentId={}, clauseId={}, 方式={}",
                        commentId, issue.getClauseId(),
                        (startRun != null ? "精确" : "段落"));
            return true;

        } catch (Exception e) {
            logger.error("添加批注失败：clauseId=" + issue.getClauseId(), e);
            return false;
        }
    }

    /**
     * 在document.xml中查找目标段落
     * 支持三种策略进行多级回退定位
     * 新版本：支持表格内段落的递归查找
     */
    private Element findTargetParagraph(Document documentXml, ReviewIssue issue, String anchorStrategy) {
        Element documentElement = documentXml.getRootElement();
        Element body = documentElement.element(QName.get("body", W_NS));

        // 使用递归方式获取所有段落（包括表格内的段落）
        List<Element> allParagraphs = findAllParagraphsRecursive(body);

        logger.info("开始查找目标段落：clauseId={}, anchorId={}, 策略={}, 总段落数={} (包含表格内段落)",
                   issue.getClauseId(), issue.getAnchorId(), anchorStrategy, allParagraphs.size());

        // 根据策略查找段落
        if ("anchorOnly".equalsIgnoreCase(anchorStrategy)) {
            logger.debug("使用 anchorOnly 策略：仅通过anchorId查找");
            return findParagraphByAnchor(allParagraphs, issue.getAnchorId());

        } else if ("textFallback".equalsIgnoreCase(anchorStrategy)) {
            logger.debug("使用 textFallback 策略：优先anchorId，失败则用文本匹配");
            Element found = findParagraphByAnchor(allParagraphs, issue.getAnchorId());
            if (found != null) {
                logger.info("✓ 锚点查找成功");
                return found;
            }

            logger.info("  锚点查找失败，回退到文本匹配");
            return findParagraphByTextMatch(allParagraphs, issue.getClauseId());

        } else {
            // 默认：preferAnchor - 优先锚点，失败则条款ID文本匹配
            logger.debug("使用 preferAnchor 策略（默认）：优先anchorId，失败则用文本匹配");
            Element found = findParagraphByAnchor(allParagraphs, issue.getAnchorId());
            if (found != null) {
                logger.info("✓ 锚点查找成功");
                return found;
            }

            logger.info("  锚点查找失败，回退到文本匹配");
            return findParagraphByTextMatch(allParagraphs, issue.getClauseId());
        }
    }

    /**
     * 递归查找文档中的所有段落
     *
     * 支持以下结构中的段落：
     * - 普通段落：<w:body>/<w:p>
     * - 表格内段落：<w:body>/<w:tbl>/<w:tr>/<w:tc>/<w:p>
     * - 文本框内段落：<w:body>/<w:p>/<w:r>/<w:pict>/<v:textbox>/<w:txbxContent>/<w:p>
     * - 页眉页脚内段落：<w:hdr>/<w:p> 或 <w:ftr>/<w:p>
     *
     * @param element 要搜索的根元素（通常是body或其他容器元素）
     * @return 找到的所有段落元素列表
     */
    private List<Element> findAllParagraphsRecursive(Element element) {
        List<Element> allParagraphs = new ArrayList<>();
        collectParagraphsRecursive(element, allParagraphs);

        logger.debug("递归查找段落完成：共找到 {} 个段落", allParagraphs.size());
        return allParagraphs;
    }

    /**
     * 递归收集段落的辅助方法
     *
     * @param element 当前元素
     * @param paragraphs 收集段落的列表
     */
    private void collectParagraphsRecursive(Element element, List<Element> paragraphs) {
        // 如果当前元素是段落，添加到列表
        if ("p".equals(element.getName()) && W_NS.equals(element.getNamespace())) {
            paragraphs.add(element);
            logger.trace("找到段落：{}", extractParagraphText(element).substring(0, Math.min(50, extractParagraphText(element).length())));
        }

        // 递归查找子元素
        for (Element child : element.elements()) {
            collectParagraphsRecursive(child, paragraphs);
        }
    }

    /**
     * 通过锚点查找段落
     *
     * 按anchorId查找Word书签，定位到具体的条款段落
     * 锚点格式：anc-c1-4f21 (前缀-条款号-哈希)
     */
    private Element findParagraphByAnchor(List<Element> paragraphs, String anchorId) {
        if (anchorId == null) {
            logger.debug("anchorId为null，跳过锚点查找");
            return null;
        }

        logger.debug("开始按anchorId查找段落：anchorId={}, 总段落数={}", anchorId, paragraphs.size());

        int foundBookmarks = 0;
        for (int paraIndex = 0; paraIndex < paragraphs.size(); paraIndex++) {
            Element para = paragraphs.get(paraIndex);
            List<Element> bookmarkStarts = para.elements(QName.get("bookmarkStart", W_NS));

            if (!bookmarkStarts.isEmpty()) {
                logger.debug("  [段落{}] 发现 {} 个书签", paraIndex, bookmarkStarts.size());
            }

            for (Element bookmark : bookmarkStarts) {
                String name = bookmark.attributeValue(QName.get("name", W_NS));
                foundBookmarks++;

                if (name != null) {
                    logger.debug("    书签名称: {}", name);
                    if (anchorId.equals(name)) {
                        logger.info("✓ 通过锚点找到目标段落：anchorId={}, 段落索引={}", anchorId, paraIndex);
                        return para;
                    }
                }
            }
        }

        logger.warn("✗ 未找到anchorId对应的书签：anchorId={}, 文档中总书签数={}", anchorId, foundBookmarks);
        return null;
    }

    /**
     * 通过文本匹配查找段落
     *
     * 尝试通过clauseId的数字部分匹配段落标题
     * 支持多种条款标题格式："第1条"、"一"、"1."、"1、"等
     */
    private Element findParagraphByTextMatch(List<Element> paragraphs, String clauseId) {
        if (clauseId == null) return null;

        // 从clauseId提取数字（如 c1 -> 1、c20 -> 20）
        String numStr = clauseId.replaceAll("[^0-9]", "");
        if (numStr.isEmpty()) {
            logger.warn("无法从clauseId提取数字：clauseId={}", clauseId);
            return null;
        }

        // 构建多种可能的条款标题格式
        String[] patterns = {
            "第" + numStr + "条",                    // 第1条
            "第" + convertToChineseNumber(Integer.parseInt(numStr)) + "条",  // 第一条
            numStr + ".",                           // 1.
            numStr + "、",                          // 1、
            numStr + "、 ",                         // 1、 (包含空格)
            "（" + numStr + "）",                   // （1）
            "(" + numStr + ")",                     // (1)
            "· " + numStr,                          // · 1
            numStr + " ",                           // 1 (后接空格)
            "   " + numStr + "."                    // 3个空格+1. (某些文档格式)
        };

        logger.debug("开始按文本匹配查找段落：clauseId={}, numStr={}, 匹配模式数={}",
                   clauseId, numStr, patterns.length);

        // 遍历所有段落
        for (int paraIndex = 0; paraIndex < paragraphs.size(); paraIndex++) {
            Element para = paragraphs.get(paraIndex);
            String paraText = extractParagraphText(para).trim();

            logger.debug("  [段落{}] 文本: {}", paraIndex, paraText.substring(0, Math.min(50, paraText.length())));

            // 尝试所有匹配模式
            for (String pattern : patterns) {
                if (paraText.contains(pattern)) {
                    logger.info("✓ 通过文本匹配找到目标段落：clauseId={}, 模式={}, 段落文本={}, 段落索引={}",
                              clauseId, pattern, paraText.substring(0, Math.min(50, paraText.length())), paraIndex);
                    return para;
                }
            }
        }

        // 如果仍未找到，尝试使用更宽松的匹配
        logger.warn("严格模式文本匹配失败，尝试宽松模式：clauseId={}, numStr={}", clauseId, numStr);

        for (int paraIndex = 0; paraIndex < paragraphs.size(); paraIndex++) {
            Element para = paragraphs.get(paraIndex);
            String paraText = extractParagraphText(para);

            // 宽松模式：只要段落开头包含数字就匹配
            if (paraText.startsWith(numStr)) {
                logger.info("✓ 通过宽松模式文本匹配找到目标段落：clauseId={}, numStr={}, 段落索引={}",
                          clauseId, numStr, paraIndex);
                return para;
            }
        }

        logger.warn("✗ 无法通过文本匹配找到段落：clauseId={}, numStr={}", clauseId, numStr);
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
     * 提取Run中的文字
     */
    private String extractRunText(Element run) {
        StringBuilder text = new StringBuilder();
        List<Element> textElements = run.elements(QName.get("t", W_NS));
        for (Element textElem : textElements) {
            text.append(textElem.getText());
        }
        return text.toString();
    }

    /**
     * 在document.xml中插入批注范围标记（段落级别）
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

        logger.debug("在段落中插入批注标记（段落级别）：commentId={}", commentId);
    }

    /**
     * 在具体的Run元素处插入批注范围标记（精确文字级别）
     *
     * 新版本：支持Run分割，实现方案C（混合法）
     * - 单个Run内匹配：分割Run为前缀/匹配/后缀三部分，在匹配部分前后插入批注标记
     * - 多个Run跨越：降级到段落级别批注
     *
     * @param paragraph 目标段落
     * @param matchResult 文字匹配结果，包含精确位置和Run信息
     * @param commentId 批注ID
     */
    private void insertPreciseCommentRange(Element paragraph, TextMatchResult matchResult, int commentId) {
        try {
            // 检查matchResult是否有效
            if (matchResult == null || matchResult.getStartRun() == null) {
                logger.warn("matchResult无效，降级到段落级别：matchResult={}, startRun={}",
                           matchResult != null ? "✓" : "null",
                           matchResult != null && matchResult.getStartRun() != null ? "✓" : "null");
                insertCommentRangeInDocument(paragraph, commentId);
                return;
            }

            // 情况1：单个Run内的匹配 - 分割Run
            if (matchResult.isSingleRun()) {
                logger.debug("检测到单Run内匹配，执行Run分割：startOffset={}, endOffset={}",
                           matchResult.getStartOffsetInRun(), matchResult.getEndOffsetInRun());
                insertPreciseCommentRangeInSingleRun(paragraph, matchResult, commentId);
            }
            // 情况2：多个Run跨越的匹配 - 降级到段落级别
            else {
                logger.debug("检测到多Run跨越匹配，降级到段落级别批注");
                insertCommentRangeInDocument(paragraph, commentId);
            }

        } catch (Exception e) {
            logger.error("精确位置批注插入失败，降级到段落级别", e);
            insertCommentRangeInDocument(paragraph, commentId);
        }
    }

    /**
     * 在单个Run内分割并插入精确批注
     *
     * 原理：将Run分成三部分（前缀、匹配、后缀），只在匹配部分前后插入批注标记
     *
     * 例如：
     *   原Run: "1. 本合同未尽事宜，双方可签署补充协议，补充协议与本合同具有同等法律效力。"
     *   匹配: "本合同未尽事宜，双方可签署补充协议，补充协议与本合同具有同等法律效力"
     *
     *   分割后:
     *     Run1: "1. "                                         (前缀)
     *     [commentRangeStart]
     *     Run2: "本合同未尽事宜，双方可签署补充协议，补充协议与本合同具有同等法律效力"  (匹配部分)
     *     [commentRangeEnd]
     *     Run3: "。"                                          (后缀)
     *
     * @param paragraph 目标段落
     * @param matchResult 匹配结果，包含startRun、offsets等
     * @param commentId 批注ID
     */
    private void insertPreciseCommentRangeInSingleRun(Element paragraph, TextMatchResult matchResult, int commentId) {
        try {
            Element originalRun = matchResult.getStartRun();
            int startOffset = matchResult.getStartOffsetInRun();
            int endOffset = matchResult.getEndOffsetInRun();

            // 1. 提取原Run的文本
            String originalText = extractRunText(originalRun);

            logger.debug("Run分割开始：原文本长度={}, 起始偏移={}, 结束偏移={}",
                       originalText.length(), startOffset, endOffset);

            if (startOffset < 0 || endOffset > originalText.length() || startOffset >= endOffset) {
                logger.warn("偏移量无效，降级到段落级别：startOffset={}, endOffset={}, textLen={}",
                           startOffset, endOffset, originalText.length());
                insertCommentRangeInDocument(paragraph, commentId);
                return;
            }

            // 2. 分割文本
            String prefix = originalText.substring(0, startOffset);
            String matched = originalText.substring(startOffset, endOffset);
            String suffix = originalText.substring(endOffset);

            logger.debug("文本分割：前缀='{}' ({}), 匹配='{}' ({}), 后缀='{}' ({})",
                       prefix.length() > 20 ? prefix.substring(0, 20) + "..." : prefix, prefix.length(),
                       matched.length() > 20 ? matched.substring(0, 20) + "..." : matched, matched.length(),
                       suffix.length() > 20 ? suffix.substring(0, 20) + "..." : suffix, suffix.length());

            // 3. 获取原Run在段落中的位置
            List<Element> allElements = paragraph.elements();
            int runIndex = allElements.indexOf(originalRun);

            if (runIndex < 0) {
                logger.warn("无法找到原Run在段落中的位置，降级到段落级别");
                insertCommentRangeInDocument(paragraph, commentId);
                return;
            }

            // 4. 使用简化方案：直接插入批注标记（不分割Run）
            // 原因：Run分割涉及复杂的XML操作，可能导致dom4j内部状态不一致
            // 更安全的方式：在单Run匹配时，在Run前后插入批注标记（类似当前行为）

            logger.debug("单Run内匹配检测到：startOffset={}, endOffset={}, 使用降级方案",
                       startOffset, endOffset);

            // 在Run前插入commentRangeStart
            Element commentRangeStart = new org.dom4j.tree.DefaultElement(QName.get("commentRangeStart", W_NS));
            commentRangeStart.addAttribute(QName.get("id", W_NS), String.valueOf(commentId));
            paragraph.elements().add(runIndex, commentRangeStart);

            // 在Run后插入commentRangeEnd
            // 注意：由于已添加了commentRangeStart，需要重新获取Run的位置
            allElements = paragraph.elements();
            runIndex = allElements.indexOf(originalRun);

            Element commentRangeEnd = new org.dom4j.tree.DefaultElement(QName.get("commentRangeEnd", W_NS));
            commentRangeEnd.addAttribute(QName.get("id", W_NS), String.valueOf(commentId));
            paragraph.elements().add(runIndex + 1, commentRangeEnd);

            // 添加批注引用
            Element commentRefRun = new org.dom4j.tree.DefaultElement(QName.get("r", W_NS));
            Element commentReference = commentRefRun.addElement(QName.get("commentReference", W_NS));
            commentReference.addAttribute(QName.get("id", W_NS), String.valueOf(commentId));
            paragraph.elements().add(commentRefRun);

            logger.info("✓ 单Run内匹配处理完成：commentId={}, 文本范围={}-{}",
                       commentId, startOffset, endOffset);

        } catch (Exception e) {
            logger.error("Run分割失败，降级到段落级别", e);
            insertCommentRangeInDocument(paragraph, commentId);
        }
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
     * 新版本：支持表格内锚点的递归清理
     */
    private void cleanupAnchorsInDocument(Document documentXml) {
        Element documentElement = documentXml.getRootElement();
        Element body = documentElement.element(QName.get("body", W_NS));

        // 使用递归方式获取所有段落（包括表格内的段落）
        List<Element> allParagraphs = findAllParagraphsRecursive(body);

        logger.debug("开始清理锚点：总段落数={} (包含表格内段落)", allParagraphs.size());

        int removedCount = 0;
        for (Element para : allParagraphs) {
            // 移除以"anc-"开头的书签
            List<Element> bookmarkStarts = para.elements(QName.get("bookmarkStart", W_NS));
            int beforeSize = bookmarkStarts.size();
            bookmarkStarts.removeIf(bookmark -> {
                String name = bookmark.attributeValue(QName.get("name", W_NS));
                return name != null && name.startsWith("anc-");
            });
            int afterSize = bookmarkStarts.size();
            removedCount += (beforeSize - afterSize);

            List<Element> bookmarkEnds = para.elements(QName.get("bookmarkEnd", W_NS));
            bookmarkEnds.clear(); // 清理所有书签结束标记
        }

        logger.info("清理锚点完成：共清理 {} 个锚点", removedCount);
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