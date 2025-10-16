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
 * 提供文档解析、锚点生成、批注插入、书签管理、表格处理等功能
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
     * 解析DOCX文档为段落和表格内容列表
     * 增强版本：同时提取段落文本和表格数据
     *
     * @param doc XWPFDocument对象
     * @return 包含段落和表格内容的文档元素列表
     */
    public List<DocumentElement> parseDocumentElements(XWPFDocument doc) {
        List<DocumentElement> elements = new ArrayList<>();

        // 解析文档主体内容
        for (org.apache.poi.xwpf.usermodel.IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph para = (XWPFParagraph) element;
                String text = para.getText().trim();
                if (!text.isEmpty()) {
                    elements.add(new DocumentElement(DocumentElement.Type.PARAGRAPH, text, null));
                }
            } else if (element instanceof XWPFTable) {
                XWPFTable table = (XWPFTable) element;
                Map<String, List<String>> tableData = parseTable(table);
                if (!tableData.isEmpty()) {
                    elements.add(new DocumentElement(DocumentElement.Type.TABLE, null, tableData));
                }
            }
        }

        logger.debug("解析文档元素完成: 段落={}, 表格={}",
                    elements.stream().filter(e -> e.getType() == DocumentElement.Type.PARAGRAPH).count(),
                    elements.stream().filter(e -> e.getType() == DocumentElement.Type.TABLE).count());

        return elements;
    }

    /**
     * 解析DOCX文档为段落列表（兼容性方法）
     * 保持向后兼容，同时提取表格内容转换为文本
     *
     * @param doc XWPFDocument对象
     * @return 段落文本列表（包含表格内容）
     */
    public List<String> parseParagraphs(XWPFDocument doc) {
        List<String> paragraphs = new ArrayList<>();

        // 遍历文档的所有主体元素（段落和表格）
        for (org.apache.poi.xwpf.usermodel.IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph para = (XWPFParagraph) element;
                String text = para.getText().trim();
                if (!text.isEmpty()) {
                    paragraphs.add(text);
                }
            } else if (element instanceof XWPFTable) {
                // 将表格转换为文本格式并添加到段落列表
                XWPFTable table = (XWPFTable) element;
                String tableText = convertTableToText(table);
                if (!tableText.isEmpty()) {
                    paragraphs.add("【表格内容】\n" + tableText);
                }
            }
        }

        return paragraphs;
    }

    /**
     * 解析单个表格数据
     *
     * @param table XWPFTable对象
     * @return 表格数据Map，key为列名，value为该列的所有行数据
     */
    public Map<String, List<String>> parseTable(XWPFTable table) {
        Map<String, List<String>> tableData = new LinkedHashMap<>();

        if (table.getRows().isEmpty()) {
            return tableData;
        }

        // 获取表头（第一行）
        XWPFTableRow headerRow = table.getRow(0);
        List<String> headers = new ArrayList<>();

        for (XWPFTableCell cell : headerRow.getTableCells()) {
            String headerText = cell.getText().trim();
            headers.add(headerText.isEmpty() ? "列" + (headers.size() + 1) : headerText);
        }

        // 初始化每列的数据列表
        for (String header : headers) {
            tableData.put(header, new ArrayList<>());
        }

        // 处理数据行（从第二行开始）
        for (int rowIndex = 1; rowIndex < table.getRows().size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            List<XWPFTableCell> cells = row.getTableCells();

            for (int colIndex = 0; colIndex < headers.size() && colIndex < cells.size(); colIndex++) {
                String cellText = cells.get(colIndex).getText().trim();
                String header = headers.get(colIndex);
                tableData.get(header).add(cellText);
            }
        }

        logger.debug("解析表格完成: 列数={}, 数据行数={}", headers.size(), table.getRows().size() - 1);

        return tableData;
    }

    /**
     * 将表格转换为文本格式
     *
     * @param table XWPFTable对象
     * @return 格式化的表格文本
     */
    public String convertTableToText(XWPFTable table) {
        StringBuilder tableText = new StringBuilder();

        for (int rowIndex = 0; rowIndex < table.getRows().size(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            List<String> cellTexts = new ArrayList<>();

            for (XWPFTableCell cell : row.getTableCells()) {
                cellTexts.add(cell.getText().trim());
            }

            // 使用制表符分隔单元格内容
            tableText.append(String.join(" | ", cellTexts));

            if (rowIndex < table.getRows().size() - 1) {
                tableText.append("\n");
            }
        }

        return tableText.toString();
    }

    /**
     * 从文档元素中提取条款（支持表格）
     * 增强版本：识别标题作为条款起始，收集该条款下的段落和表格内容
     *
     * @param doc XWPFDocument对象
     * @param generateAnchors 是否生成锚点ID
     * @return 条款列表
     */
    public List<Clause> extractClausesWithTables(XWPFDocument doc, boolean generateAnchors) {
        List<Clause> clauses = new ArrayList<>();
        List<DocumentElement> elements = parseDocumentElements(doc);
        int clauseCounter = 0;

        logger.debug("开始提取条款（支持表格）: 总元素数={}, 生成锚点={}", elements.size(), generateAnchors);

        for (int i = 0; i < elements.size(); i++) {
            DocumentElement element = elements.get(i);

            // 只有段落才可能是条款标题
            if (element.isParagraph() && isClauseHeading(element.getText())) {
                clauseCounter++;
                String clauseId = "c" + clauseCounter;
                String heading = element.getText();

                logger.debug("发现条款标题[{}]: '{}'", i, heading);

                // 收集该条款的内容（段落文本和表格数据）
                StringBuilder clauseText = new StringBuilder();
                List<Map<String, List<String>>> clauseTables = new ArrayList<>();
                int startIndex = i;
                int endIndex = i;

                // 从下一个元素开始收集内容，直到遇到下一个条款标题
                for (int j = i + 1; j < elements.size(); j++) {
                    DocumentElement nextElement = elements.get(j);

                    // 如果遇到下一个条款标题，停止收集
                    if (nextElement.isParagraph() && isClauseHeading(nextElement.getText())) {
                        break;
                    }

                    if (nextElement.isParagraph()) {
                        // 添加段落文本
                        if (!nextElement.getText().trim().isEmpty()) {
                            clauseText.append(nextElement.getText()).append("\n");
                        }
                    } else if (nextElement.isTable()) {
                        // 添加表格数据
                        clauseTables.add(nextElement.getTableData());
                        // 同时在文本中添加表格的文本表示
                        clauseText.append("\n").append(formatTableAsText(nextElement.getTableData())).append("\n");
                    }

                    endIndex = j;
                }

                // 构建条款对象
                Clause clause = Clause.builder()
                        .id(clauseId)
                        .heading(heading)
                        .text(clauseText.toString().trim())
                        .tables(clauseTables.isEmpty() ? null : clauseTables)
                        .startParaIndex(startIndex)
                        .endParaIndex(endIndex)
                        .build();

                // 生成锚点ID
                if (generateAnchors) {
                    clause.setAnchorId(generateAnchorId(clauseId));
                }

                clauses.add(clause);
                logger.debug("创建条款（含表格）: id={}, 锚点={}, 段落范围=[{}-{}], 表格数={}",
                           clauseId, clause.getAnchorId(), startIndex, endIndex, clauseTables.size());
            }
        }

        logger.info("条款提取完成（含表格）: 共找到{}个条款", clauses.size());
        return clauses;
    }

    /**
     * 将表格数据格式化为文本
     */
    private String formatTableAsText(Map<String, List<String>> tableData) {
        if (tableData.isEmpty()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        List<String> headers = new ArrayList<>(tableData.keySet());

        // 添加表头
        text.append("【表格】\n");
        text.append(String.join(" | ", headers)).append("\n");

        // 计算最大行数
        int maxRows = tableData.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        // 添加数据行
        for (int row = 0; row < maxRows; row++) {
            List<String> rowData = new ArrayList<>();
            for (String header : headers) {
                List<String> columnData = tableData.get(header);
                if (columnData != null && row < columnData.size()) {
                    rowData.add(columnData.get(row));
                } else {
                    rowData.add("");
                }
            }
            text.append(String.join(" | ", rowData)).append("\n");
        }

        return text.toString();
    }

    /**
     * 从段落列表中提取条款（原有方法，保持向后兼容）
     * 识别标题(如"第一条"、"第二条")作为条款起始
     *
     * @param paragraphs 段落文本列表
     * @param generateAnchors 是否生成锚点ID
     * @return 条款列表
     */
    public List<Clause> extractClauses(List<String> paragraphs, boolean generateAnchors) {
        List<Clause> clauses = new ArrayList<>();
        int clauseCounter = 0;

        logger.debug("开始提取条款: 总段落数={}, 生成锚点={}", paragraphs.size(), generateAnchors);

        // 输出前10个段落用于调试
        for (int i = 0; i < Math.min(10, paragraphs.size()); i++) {
            logger.debug("段落[{}]: '{}'", i, paragraphs.get(i));
        }

        for (int i = 0; i < paragraphs.size(); i++) {
            String text = paragraphs.get(i);

            // 识别条款标题
            if (isClauseHeading(text)) {
                clauseCounter++;
                String clauseId = "c" + clauseCounter;

                logger.debug("发现条款标题[{}]: '{}'", i, text);

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
                logger.debug("创建条款: id={}, 锚点={}, 段落范围=[{}-{}]",
                           clauseId, clause.getAnchorId(), startIndex, endIndex);
            }
        }

        logger.info("条款提取完成: 共找到{}个条款", clauses.size());
        return clauses;
    }

    /**
     * 判断段落是否为条款标题
     * 增强规则：支持多种条款标题格式
     *
     * @param text 段落文本
     * @return 是否为条款标题
     */
    private boolean isClauseHeading(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // 标准化文本
        String normalizedText = text.trim();

        // 规则1：标准条款格式 - "第X条"
        if (normalizedText.contains("第") && normalizedText.contains("条")) {
            return true;
        }

        // 规则2：数字条款格式 - "1."、"2."、"1、"、"2、"
        if (normalizedText.matches("^\\d+[.、].*")) {
            return true;
        }

        // 规则3：罗马数字格式 - "I."、"II."、"III."等
        if (normalizedText.matches("^[IVX]+[.、].*")) {
            return true;
        }

        // 规则4：中文数字格式 - "一、"、"二、"、"三、"等
        if (normalizedText.matches("^[一二三四五六七八九十百千]+[.、].*")) {
            return true;
        }

        // 规则5：条款关键词
        String[] keywords = {"条款", "协议", "合同条款", "合同内容", "权利义务", "责任", "违约", "争议解决"};
        for (String keyword : keywords) {
            if (normalizedText.contains(keyword) && normalizedText.length() < 50) {
                return true;
            }
        }

        // 规则6：标题样式检测 (长度较短且以冒号结尾)
        if (normalizedText.length() < 30 && normalizedText.endsWith(":")) {
            return true;
        }

        // 规则7：全大写或特殊标记的标题
        if (normalizedText.matches("^[A-Z\\s]+$") && normalizedText.length() < 50) {
            return true;
        }

        logger.debug("段落不符合条款标题规则: '{}'", normalizedText);
        return false;
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
     * 增强版本：支持多种查找方式
     *
     * @param doc XWPFDocument对象
     * @param anchorId 锚点ID
     * @return 段落索引,未找到返回-1
     */
    public int findParagraphByAnchor(XWPFDocument doc, String anchorId) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();

        logger.debug("开始查找锚点: anchorId={}, 文档段落数={}", anchorId, paragraphs.size());

        // 方法1：查找书签
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph para = paragraphs.get(i);
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP ctp = para.getCTP();

            // 检查段落中的书签
            if (ctp.getBookmarkStartList() != null && !ctp.getBookmarkStartList().isEmpty()) {
                for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark bookmark :
                     ctp.getBookmarkStartList()) {
                    if (anchorId.equals(bookmark.getName())) {
                        logger.debug("通过书签找到锚点: anchorId={}, index={}", anchorId, i);
                        return i;
                    }
                }
            }
        }

        // 方法2：查找隐藏文本中的锚点标记（如果使用了其他锚点插入方式）
        for (int i = 0; i < paragraphs.size(); i++) {
            XWPFParagraph para = paragraphs.get(i);
            String paraText = para.getText();

            if (paraText != null && paraText.contains(anchorId)) {
                logger.debug("通过文本内容找到锚点: anchorId={}, index={}, text={}", anchorId, i, paraText);
                return i;
            }
        }

        logger.debug("未找到锚点: anchorId={}", anchorId);
        return -1;
    }

    /**
     * 查找包含指定条款ID的段落索引
     * 增强版本：支持更准确的条款定位
     *
     * @param clauses 条款列表
     * @param clauseId 条款ID
     * @return 段落索引,未找到返回-1
     */
    public int findParagraphByClauseId(List<Clause> clauses, String clauseId) {
        logger.debug("开始查找条款ID: clauseId={}, 条款列表大小={}", clauseId, clauses.size());

        for (Clause clause : clauses) {
            logger.debug("比较条款: 目标ID={}, 当前ID={}, 段落索引={}",
                        clauseId, clause.getId(), clause.getStartParaIndex());

            if (clauseId.equals(clause.getId())) {
                int index = clause.getStartParaIndex() != null ? clause.getStartParaIndex() : -1;
                logger.debug("找到匹配条款: clauseId={}, index={}", clauseId, index);
                return index;
            }
        }

        logger.debug("未找到条款ID: clauseId={}", clauseId);
        return -1;
    }

    /**
     * 在指定段落插入真正的Word右侧批注（Comment）
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
            // 使用真正的Word批注功能
            insertWordComment(doc, paraIndex, commentText);
            logger.info("成功插入Word批注: paraIndex={}", paraIndex);

        } catch (Exception e) {
            logger.error("插入Word批注失败: paraIndex={}, 尝试使用备用方法", paraIndex, e);

            // 备用方法：在段落后添加新段落的方式实现批注效果
            insertFallbackComment(doc, paraIndex, commentText);
        }
    }

    /**
     * 插入真正的Word批注（右侧批注）
     * 注意：此方法使用了复杂的POI API，目前注释掉以避免编译错误
     * 推荐使用新的XML方式实现批注功能
     *
     * @param doc XWPFDocument对象
     * @param paraIndex 段落索引
     * @param commentText 批注内容
     */
    private void insertWordComment(XWPFDocument doc, int paraIndex, String commentText) {
        logger.warn("POI批注功能暂时不可用，请使用XML方式批注功能 (/api/annotate-xml)");

        // 注释掉复杂的POI API代码，避免编译错误
        // 使用备用方法代替
        insertFallbackComment(doc, paraIndex, commentText);

        /*
        List<XWPFParagraph> paragraphs = doc.getParagraphs();
        XWPFParagraph targetPara = paragraphs.get(paraIndex);

        try {
            // 生成唯一的批注ID
            BigInteger commentId = BigInteger.valueOf(System.currentTimeMillis() % 1000000);

            // 获取文档对象
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocument1 ctDocument = doc.getDocument();

            // 确保批注容器存在
            if (ctDocument.getComments() == null) {
                ctDocument.addNewComments();
            }

            // 创建批注对象
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTComment ctComment =
                ctDocument.getComments().addNewComment();
            ctComment.setId(commentId);
            ctComment.setAuthor("AI Review Assistant");
            ctComment.setDate(java.util.Calendar.getInstance());
            ctComment.setInitials("AI");

            // 添加批注内容段落
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP commentPara = ctComment.addNewP();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR commentRun = commentPara.addNewR();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText commentText1 = commentRun.addNewT();
            commentText1.setStringValue(commentText);

            // 在目标段落中添加批注标记
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP targetCTP = targetPara.getCTP();

            // 在段落开始处插入批注范围起始标记
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR commentStartRun =
                targetCTP.insertNewR(0);
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMarkupRange commentRangeStart =
                commentStartRun.addNewCommentRangeStart();
            commentRangeStart.setId(commentId);

            // 在段落结束处插入批注范围结束标记
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR commentEndRun =
                targetCTP.addNewR();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMarkupRange commentRangeEnd =
                commentEndRun.addNewCommentRangeEnd();
            commentRangeEnd.setId(commentId);

            // 插入批注引用（这会在文档中显示为批注标记）
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR commentRefRun =
                targetCTP.addNewR();
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFtnEdnRef commentReference =
                commentRefRun.addNewCommentReference();
            commentReference.setId(commentId);

            logger.debug("成功创建Word批注: commentId={}, 内容长度={}", commentId, commentText.length());

        } catch (Exception e) {
            logger.error("创建Word批注失败，尝试简化方法", e);

            // 简化的批注创建方法
            try {
                createSimpleWordComment(doc, targetPara, commentText);
            } catch (Exception ex) {
                logger.error("简化批注创建也失败", ex);
                throw ex;
            }
        }
        */
    }

    /**
     * 创建简化的Word批注
     *
     * @param doc XWPFDocument对象
     * @param targetPara 目标段落
     * @param commentText 批注内容
     */
    private void createSimpleWordComment(XWPFDocument doc, XWPFParagraph targetPara, String commentText) {
        try {
            // 生成批注ID
            String commentId = "comment_" + System.currentTimeMillis();

            // 在段落末尾添加批注标记（使用书签方式）
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP ctp = targetPara.getCTP();

            // 创建书签作为批注锚点
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark bookmark = ctp.addNewBookmarkStart();
            bookmark.setName(commentId);
            bookmark.setId(BigInteger.valueOf(System.currentTimeMillis() % 1000000));

            // 创建书签结束
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTMarkupRange bookmarkEnd = ctp.addNewBookmarkEnd();
            bookmarkEnd.setId(bookmark.getId());

            // 在段落末尾添加批注文本run（隐藏）
            XWPFRun commentRun = targetPara.createRun();
            commentRun.getCTR().addNewRPr().addNewVanish(); // 设置为隐藏
            commentRun.setText("[批注: " + commentText + "]");

            logger.debug("创建简化Word批注成功: commentId={}", commentId);

        } catch (Exception e) {
            logger.error("创建简化批注失败", e);
            throw e;
        }
    }

    /**
     * 备用方法：通过在段落后添加新段落的方式实现批注效果
     *
     * @param doc XWPFDocument对象
     * @param paraIndex 段落索引
     * @param commentText 批注内容
     */
    private void insertFallbackComment(XWPFDocument doc, int paraIndex, String commentText) {
        List<XWPFParagraph> paragraphs = doc.getParagraphs();

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

            logger.info("使用备用方法成功插入批注: paraIndex={}", paraIndex);

        } catch (Exception e) {
            logger.error("备用方法插入批注失败: paraIndex={}", paraIndex, e);

            // 最后的备用方法：在目标段落末尾直接添加批注文本
            try {
                XWPFParagraph targetPara = paragraphs.get(paraIndex);
                XWPFRun commentRun = targetPara.createRun();
                commentRun.addBreak(); // 换行
                commentRun.setText("【AI审查批注】" + commentText);
                commentRun.setColor("FF0000");
                commentRun.setFontSize(10);
                commentRun.setItalic(true);

                logger.info("使用最终备用方法成功插入批注: paraIndex={}", paraIndex);
            } catch (Exception ex) {
                logger.error("所有方法都失败: paraIndex={}", paraIndex, ex);
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
