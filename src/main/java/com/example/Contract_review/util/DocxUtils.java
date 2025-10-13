package com.example.Contract_review.util;

import com.example.Contract_review.model.Clause;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Word文档处理工具类
 *
 * 提供文档解析、锚点生成、批注插入、书签管理等功能
 */
@Component
public class DocxUtils {

    private static final Logger logger = LoggerFactory.getLogger(DocxUtils.class);

    /**
     * 从输入流中加载DOCX文档
     *
     * @param inputStream 文档输入流
     * @return XWPFDocument对象
     * @throws IOException 读取失败
     */
    public XWPFDocument loadDocx(InputStream inputStream) throws IOException {
        return new XWPFDocument(inputStream);
    }

    /**
     * 从输入流中加载DOC文档(旧版Word格式)
     *
     * @param inputStream 文档输入流
     * @return HWPFDocument对象
     * @throws IOException 读取失败
     */
    public HWPFDocument loadDoc(InputStream inputStream) throws IOException {
        return new HWPFDocument(inputStream);
    }

    /**
     * 解析DOC文档(旧版Word格式)为文本列表
     *
     * @param doc HWPFDocument对象
     * @return 段落文本列表
     */
    public List<String> parseDocParagraphs(HWPFDocument doc) {
        List<String> paragraphs = new ArrayList<>();
        Range range = doc.getRange();

        for (int i = 0; i < range.numParagraphs(); i++) {
            Paragraph para = range.getParagraph(i);
            String text = para.text().trim();
            paragraphs.add(text);
        }

        return paragraphs;
    }

    /**
     * 解析DOCX文档为段落列表
     *
     * @param doc XWPFDocument对象
     * @return 段落文本列表
     */
    public List<String> parseParagraphs(XWPFDocument doc) {
        List<String> paragraphs = new ArrayList<>();

        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getText().trim();
            paragraphs.add(text);
        }

        return paragraphs;
    }

    /**
     * 从段落列表中提取条款
     * 识别标题(如"第一条"、"第二条")作为条款起始
     *
     * @param paragraphs 段落文本列表
     * @param generateAnchors 是否生成锚点ID
     * @return 条款列表
     */
    public List<Clause> extractClauses(List<String> paragraphs, boolean generateAnchors) {
        List<Clause> clauses = new ArrayList<>();
        int clauseCounter = 0;

        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i);

            // 识别条款标题(包含"第"和"条"的段落)
            if (isClauseHeading(text)) {
                clauseCounter++;
                String clauseId = "c" + clauseCounter;

                // 收集该条款的内容(从标题到下一个标题之前)
                StringBuilder clauseText = new StringBuilder();
                int startIndex = i;
                int endIndex = i;

                for (int j = i + 1; j < paragraphs.size(); j++) {
                    if (isClauseHeading(paragraphs.get(j))) {
                        break;
                    }
                    clauseText.append(paragraphs.get(j)).append("\n");
                    endIndex = j;
                }

                // 构建条款对象
                Clause clause = Clause.builder()
                        .id(clauseId)
                        .heading(text)
                        .text(clauseText.toString().trim())
                        .startParaIndex(startIndex)
                        .endParaIndex(endIndex)
                        .build();

                // 生成锚点ID
                if (generateAnchors) {
                    clause.setAnchorId(generateAnchorId(clauseId));
                }

                clauses.add(clause);
            }
        }

        return clauses;
    }

    /**
     * 判断段落是否为条款标题
     * 简单规则:包含"第"和"条"的段落
     *
     * @param text 段落文本
     * @return 是否为条款标题
     */
    private boolean isClauseHeading(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("第") && text.contains("条");
    }

    /**
     * 生成锚点ID
     * 格式: anc-{clauseId}-{shortHash}
     *
     * @param clauseId 条款ID
     * @return 锚点ID
     */
    public String generateAnchorId(String clauseId) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String input = clauseId + timestamp;

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());
            String fullHash = String.format("%032x", new BigInteger(1, hash));
            String shortHash = fullHash.substring(0, 4);

            return "anc-" + clauseId + "-" + shortHash;
        } catch (NoSuchAlgorithmException e) {
            logger.error("生成锚点ID失败", e);
            return "anc-" + clauseId + "-" + UUID.randomUUID().toString().substring(0, 4);
        }
    }

    /**
     * 在文档中插入锚点书签
     *
     * @param doc XWPFDocument对象
     * @param clauses 条款列表
     */
    public void insertAnchors(XWPFDocument doc, List<Clause> clauses) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();

        for (Clause clause : clauses) {
            if (clause.getAnchorId() == null || clause.getStartParaIndex() == null) {
                continue;
            }

            int paraIndex = clause.getStartParaIndex();
            if (paraIndex >= 0 && paraIndex < paragraphs.size()) {
                XWPFParagraph para = paragraphs.get(paraIndex);

                // 在段落开头插入书签
                addBookmarkToParagraph(para, clause.getAnchorId());
            }
        }
    }

    /**
     * 向段落添加书签
     *
     * @param paragraph 段落对象
     * @param bookmarkName 书签名称
     */
    private void addBookmarkToParagraph(XWPFParagraph paragraph, String bookmarkName) {
        // 获取段落的CTP对象
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP ctp = paragraph.getCTP();

        // 创建书签起始标记
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark bookmarkStart =
            ctp.addNewBookmarkStart();
        bookmarkStart.setName(bookmarkName);
        bookmarkStart.setId(BigInteger.valueOf(System.currentTimeMillis() % 1000000));

        // 创建书签结束标记
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMarkupRange bookmarkEnd =
            ctp.addNewBookmarkEnd();
        bookmarkEnd.setId(bookmarkStart.getId());
    }

    /**
     * 查找包含指定锚点的段落索引
     *
     * @param doc XWPFDocument对象
     * @param anchorId 锚点ID
     * @return 段落索引,未找到返回-1
     */
    public int findParagraphByAnchor(XWPFDocument doc, String anchorId) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();

        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph para = paragraphs.get(i);
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP ctp = para.getCTP();

            // 检查段落中的书签
            if (ctp.getBookmarkStartList() != null) {
                for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark bookmark :
                     ctp.getBookmarkStartList()) {
                    if (anchorId.equals(bookmark.getName())) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    /**
     * 查找包含指定条款ID的段落索引
     *
     * @param clauses 条款列表
     * @param clauseId 条款ID
     * @return 段落索引,未找到返回-1
     */
    public int findParagraphByClauseId(List<Clause> clauses, String clauseId) {
        for (Clause clause : clauses) {
            if (clauseId.equals(clause.getId())) {
                return clause.getStartParaIndex() != null ? clause.getStartParaIndex() : -1;
            }
        }
        return -1;
    }

    /**
     * 在指定段落插入批注
     * 通过在段落后添加新段落的方式实现批注效果
     *
     * @param doc XWPFDocument对象
     * @param paraIndex 段落索引
     * @param commentText 批注内容
     */
    public void insertComment(XWPFDocument doc, int paraIndex, String commentText) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();

        if (paraIndex < 0 || paraIndex >= paragraphs.size()) {
            logger.warn("段落索引越界: {}", paraIndex);
            return;
        }

        try {
            // 获取文档的主体部分
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody body = doc.getDocument().getBody();

            // 在指定位置后插入新段落
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP newP = body.insertNewP(paraIndex + 1);
            XWPFParagraph commentPara = new XWPFParagraph(newP, doc);

            // 创建批注文本run
            XWPFRun run = commentPara.createRun();
            run.setText("【AI审查批注】" + commentText);
            run.setColor("FF0000"); // 红色
            run.setFontSize(10);
            run.setItalic(true);

            // 设置段落缩进
            commentPara.setIndentationLeft(400);

            logger.info("成功插入批注: paraIndex={}", paraIndex);

        } catch (Exception e) {
            logger.error("插入批注失败: paraIndex={}, 尝试使用备用方法", paraIndex, e);

            // 备用方法：在目标段落末尾直接添加批注文本
            try {
                XWPFParagraph targetPara = paragraphs.get(paraIndex);
                XWPFRun commentRun = targetPara.createRun();
                commentRun.addBreak(); // 换行
                commentRun.setText("【AI审查批注】" + commentText);
                commentRun.setColor("FF0000");
                commentRun.setFontSize(10);
                commentRun.setItalic(true);

                logger.info("使用备用方法成功插入批注: paraIndex={}", paraIndex);
            } catch (Exception ex) {
                logger.error("备用方法也失败: paraIndex={}", paraIndex, ex);
            }
        }
    }

    /**
     * 清理文档中的所有锚点书签
     *
     * @param doc XWPFDocument对象
     */
    public void cleanupAnchors(XWPFDocument doc) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();

        for (XWPFParagraph para : paragraphs) {
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP ctp = para.getCTP();

            // 移除以"anc-"开头的书签
            if (ctp.getBookmarkStartList() != null) {
                List<org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark> toRemove =
                    new ArrayList<>();

                for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark bookmark :
                     ctp.getBookmarkStartList()) {
                    if (bookmark.getName() != null && bookmark.getName().startsWith("anc-")) {
                        toRemove.add(bookmark);
                    }
                }

                for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark bookmark : toRemove) {
                    ctp.getBookmarkStartList().remove(bookmark);
                }
            }

            // 移除对应的书签结束标记
            if (ctp.getBookmarkEndList() != null) {
                ctp.getBookmarkEndList().clear();
            }
        }
    }

    /**
     * 统计文档字数
     *
     * @param doc XWPFDocument对象
     * @return 字数
     */
    public int countWords(XWPFDocument doc) {
        int wordCount = 0;
        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getText();
            if (text != null && !text.isEmpty()) {
                wordCount += text.length();
            }
        }
        return wordCount;
    }

    /**
     * 统计文档段落数
     *
     * @param doc XWPFDocument对象
     * @return 段落数
     */
    public int countParagraphs(XWPFDocument doc) {
        return doc.getParagraphs().size();
    }

    /**
     * 提取文档标题
     * 默认使用第一个非空段落作为标题
     *
     * @param doc XWPFDocument对象
     * @return 文档标题
     */
    public String extractTitle(XWPFDocument doc) {
        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getText().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "未命名文档";
    }

    /**
     * 将文档写入字节数组
     *
     * @param doc XWPFDocument对象
     * @return 文档字节数组
     * @throws IOException 写入失败
     */
    public byte[] writeToBytes(XWPFDocument doc) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.write(baos);
            return baos.toByteArray();
        }
    }
}
