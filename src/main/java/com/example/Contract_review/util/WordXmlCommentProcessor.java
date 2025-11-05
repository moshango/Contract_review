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
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.apache.poi.openxml4j.util.ZipSecureFile;

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

    static {
        try {
            double newRatio = 0.0001d;
            ZipSecureFile.setMinInflateRatio(newRatio);
            LoggerFactory.getLogger(WordXmlCommentProcessor.class)
                .info("ZipSecureFile inflate ratio lowered to {} to allow embedded fonts", newRatio);
        } catch (Exception e) {
            LoggerFactory.getLogger(WordXmlCommentProcessor.class)
                .warn("Failed to adjust ZipSecureFile min inflate ratio", e);
        }
    }

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
     * 创建XML输出格式配置
     */
    private OutputFormat createOutputFormat() {
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("UTF-8");
        format.setOmitEncoding(false);
        format.setSuppressDeclaration(false);
        return format;
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

        // 输入校验
        if (docxBytes == null || docxBytes.length == 0) {
            throw new IllegalArgumentException("文档字节数组为空");
        }

        if (issues == null || issues.isEmpty()) {
            logger.warn("没有要添加的批注，直接返回原始文档");
            return docxBytes;
        }

        // 打开DOCX包
        try (InputStream docxStream = new ByteArrayInputStream(docxBytes);
             OPCPackage opcPackage = OPCPackage.open(docxStream)) {

            // 处理document.xml
            Document documentXml = loadDocumentXml(opcPackage);
            if (documentXml == null) {
                throw new IllegalArgumentException("无法加载document.xml，文档可能损坏");
            }

            // 处理comments.xml（如果不存在则创建）
            Document commentsXml = loadOrCreateCommentsXml(opcPackage);
            if (commentsXml == null) {
                throw new IllegalArgumentException("无法创建comments.xml");
            }

            // 【修复】重新计算批注ID起始值，避免与现有批注冲突
            initializeCommentIdCounter(commentsXml);

            // 为每个问题添加批注
            int addedCount = 0;
            int failedCount = 0;
            for (ReviewIssue issue : issues) {
                try {
                    if (addCommentForIssue(documentXml, commentsXml, issue, anchorStrategy)) {
                        addedCount++;
                    } else {
                        failedCount++;
                    }
                } catch (Exception e) {
                    logger.error("添加批注失败，继续处理下一个：clauseId={}, 错误: {}",
                               issue.getClauseId(), e.getMessage());
                    failedCount++;
                }
            }

            if (addedCount == 0) {
                logger.warn("⚠️ 没有成功添加任何批注（共{}个失败），请检查文档内容是否匹配", failedCount);
            }

            // 清理锚点（如果需要）
            if (cleanupAnchors) {
                cleanupAnchorsInDocument(documentXml);
                logger.info("已清理文档中的锚点标记");
            }

            // 保存修改后的XML
            saveDocumentXml(opcPackage, documentXml);

            // 【维测】document.xml保存后验证批注标记
            Element documentRoot = documentXml.getRootElement();
            Element body = documentRoot.element(QName.get("body", W_NS));

            // 【关键修复】使用递归查找所有段落中的批注标记，而不是只查直接子元素
            List<Element> allCommentRangeStarts = findAllElementsRecursive(body, QName.get("commentRangeStart", W_NS));
            List<Element> allCommentRangeEnds = findAllElementsRecursive(body, QName.get("commentRangeEnd", W_NS));
            List<Element> allCommentRefs = findAllElementsRecursive(body, QName.get("commentReference", W_NS));

            logger.info("【维测】document.xml保存后诊断：commentRangeStart数量={}, commentRangeEnd数量={}, commentReference数量={}",
                       allCommentRangeStarts.size(), allCommentRangeEnds.size(), allCommentRefs.size());

            // 列出所有批注ID
            List<String> startIds = allCommentRangeStarts.stream()
                .map(e -> e.attributeValue(QName.get("id", W_NS)))
                .toList();
            List<String> endIds = allCommentRangeEnds.stream()
                .map(e -> e.attributeValue(QName.get("id", W_NS)))
                .toList();
            List<String> refIds = allCommentRefs.stream()
                .map(e -> e.attributeValue(QName.get("id", W_NS)))
                .toList();

            logger.debug("【维测】commentRangeStart IDs: {}", startIds);
            logger.debug("【维测】commentRangeEnd IDs: {}", endIds);
            logger.debug("【维测】commentReference IDs: {}", refIds);

            // 【维测】保存前输出comments.xml的结构和内容
            Element commentsRoot = commentsXml.getRootElement();
            List<Element> comments = commentsRoot.elements(QName.get("comment", W_NS));
            logger.info("【维测】保存comments.xml前诊断：根元素={}, 批注数量={}, 已添加批注ID={}",
                       commentsRoot.getName(), comments.size(),
                       comments.stream().map(c -> c.attributeValue(QName.get("id", W_NS))).toList());

            // 输出每个批注的内容（前100字）
            for (Element comment : comments) {
                String commentId = comment.attributeValue(QName.get("id", W_NS));
                String commentContent = comment.asXML();
                int contentLen = commentContent.length();
                String preview = contentLen > 100 ? commentContent.substring(0, 100) + "..." : commentContent;
                logger.debug("【维测】批注ID={}, 内容预览(总长度={}): {}", commentId, contentLen, preview);
            }

            saveCommentsXml(opcPackage, commentsXml);

            // 【维测】保存后验证
            logger.info("【维测】comments.xml保存后验证：文件是否存在于OPCPackage中");
            try {
                PackagePartName commentsPartName = PackagingURIHelper.createPartName("/word/comments.xml");
                PackagePart commentsPart = opcPackage.getPart(commentsPartName);
                if (commentsPart != null) {
                    long partSize = commentsPart.getSize();
                    logger.info("【维测】comments.xml在OPCPackage中，大小: {} 字节", partSize);
                } else {
                    logger.error("【维测】❌ ERROR: comments.xml在OPCPackage中为null!");
                }
            } catch (Exception e) {
                logger.error("【维测】❌ ERROR: 验证comments.xml时异常: {}", e.getMessage());
            }

            // 更新关系文件
            updateDocumentRels(opcPackage);

            // 【关键修复4】保存前验证关键文件
            logger.info("【维测】OPCPackage保存前诊断：");
            try {
                // 验证 document.xml.rels 是否包含 comments 关系
                PackagePartName relsPartName = PackagingURIHelper.createPartName("/word/_rels/document.xml.rels");
                PackagePart relsPart = opcPackage.getPart(relsPartName);
                if (relsPart != null) {
                    SAXReader reader = new SAXReader();
                    Document relsDoc = reader.read(relsPart.getInputStream());
                    Element relationships = relsDoc.getRootElement();
                    
                    boolean hasComments = false;
                    for (Element rel : relationships.elements()) {
                        String target = rel.attributeValue("Target");
                        if ("comments.xml".equals(target)) {
                            hasComments = true;
                            String relId = rel.attributeValue("Id");
                            logger.info("【维测】✓ 保存前验证：document.xml.rels包含comments关系 (Id={})", relId);
                            break;
                        }
                    }
                    
                    if (!hasComments) {
                        logger.error("【维测】❌ ERROR: 保存前验证失败：document.xml.rels不包含comments关系！");
                        // 紧急修复：尝试再次添加
                        logger.warn("【维测】尝试紧急修复：重新添加comments关系...");
                        updateDocumentRels(opcPackage);
                    }
                } else {
                    logger.error("【维测】❌ ERROR: 保存前验证：document.xml.rels为null!");
                }
            } catch (Exception preSaveEx) {
                logger.error("【维测】❌ ERROR: 保存前验证失败: {}", preSaveEx.getMessage());
            }

            // 【关键修复6】在保存前读取 document.xml.rels 的字节数组
            byte[] relsBytes = null;
            try {
                PackagePartName relsPartName = PackagingURIHelper.createPartName("/word/_rels/document.xml.rels");
                PackagePart relsPart = opcPackage.getPart(relsPartName);
                try (java.io.InputStream is = relsPart.getInputStream()) {
                    relsBytes = is.readAllBytes();
                    logger.info("【维测】✓ 读取 document.xml.rels 成功，大小: {} 字节", relsBytes.length);
                }
            } catch (Exception e) {
                logger.error("【维测】❌ ERROR: 无法读取 document.xml.rels: {}", e.getMessage());
            }

            // 输出修改后的DOCX
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                opcPackage.save(outputStream);
                logger.debug("OPCPackage保存成功，大小: {} 字节", outputStream.size());

                // 【关键修复7】如果 ZIP 中缺少 document.xml.rels，手动添加
                if (relsBytes != null) {
                    try {
                        byte[] originalZipBytes = outputStream.toByteArray();
                        logger.debug("【维测】修复前ZIP大小: {} 字节", originalZipBytes.length);
                        
                        byte[] fixedZip = manuallyAddRelsToZip(originalZipBytes, relsBytes);
                        logger.debug("【维测】修复后ZIP大小: {} 字节", fixedZip.length);
                        
                        // 直接替换 outputStream 的内容
                        outputStream.reset();
                        outputStream.write(fixedZip);
                        outputStream.flush();
                        
                        logger.info("【维测】✓ 手动修复 ZIP 文件成功");
                    } catch (Exception fixEx) {
                        logger.error("【维测】❌ ERROR: 手动修复失败: {}", fixEx.getMessage(), fixEx);
                    }
                }

                // 【关键修复5】验证保存后的ZIP文件内容
                logger.info("【维测】OPCPackage保存后诊断：");
                try {
                    // 验证保存后的ZIP文件是否包含关键文件
                    java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(
                        new java.io.ByteArrayInputStream(outputStream.toByteArray())
                    );
                    java.util.zip.ZipEntry entry;
                    boolean hasRels = false;
                    boolean hasComments = false;
                    int relsSize = 0;
                    
                    while ((entry = zipIn.getNextEntry()) != null) {
                        if ("word/_rels/document.xml.rels".equals(entry.getName())) {
                            hasRels = true;
                            relsSize = (int) entry.getSize();
                        }
                        if ("word/comments.xml".equals(entry.getName())) {
                            hasComments = true;
                        }
                    }
                    zipIn.close();
                    
                    logger.info("【维测】✓ ZIP文件验证：hasRels={}, hasComments={}, relsSize={}字节", 
                                hasRels, hasComments, relsSize);
                    
                    if (!hasRels) {
                        logger.error("【维测】❌ ERROR: ZIP文件中缺少 document.xml.rels！");
                    } else if (!hasComments) {
                        logger.error("【维测】❌ ERROR: ZIP文件中缺少 comments.xml！");
                    } else {
                        logger.info("【维测】✓ ZIP文件结构验证通过");
                    }
                } catch (Exception zipEx) {
                    logger.error("【维测】❌ ERROR: ZIP验证失败: {}", zipEx.getMessage());
                }

            } finally {
                // 【关键修复】必须关闭OPCPackage，确保所有缓冲区完全刷新
                // 这对长文档特别重要，否则comments.xml可能无法完全写入
                if (opcPackage != null) {
                    try {
                        opcPackage.close();
                        logger.debug("OPCPackage已关闭");
                    } catch (Exception closeEx) {
                        logger.warn("关闭OPCPackage时出错", closeEx);
                    }
                }
            }

            logger.info("XML批注处理完成：成功添加{}个批注，失败{}个", addedCount, failedCount);
            return outputStream.toByteArray();
        } catch (Exception e) {
            logger.error("批注处理失败：{}", e.getMessage(), e);
            throw new Exception("批注处理异常：" + e.getMessage(), e);
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
     * 初始化批注ID计数器
     * 【修复】扫描现有comments.xml中的所有批注ID，确保新批注ID不会冲突
     */
    private void initializeCommentIdCounter(Document commentsXml) {
        try {
            if (commentsXml == null) {
                logger.warn("comments.xml为null，使用默认批注ID计数器");
                return;
            }

            Element commentsRoot = commentsXml.getRootElement();
            if (commentsRoot == null) {
                logger.warn("comments根元素为null，使用默认批注ID计数器");
                return;
            }

            // 获取所有现有批注元素
            List<Element> comments = commentsRoot.elements(QName.get("comment", W_NS));

            if (comments.isEmpty()) {
                logger.debug("comments.xml中没有现有批注，重置计数器为1");
                commentIdCounter.set(1);
                return;
            }

            // 找到最大的ID
            int maxId = 0;
            for (Element comment : comments) {
                try {
                    String idStr = comment.attributeValue(QName.get("id", W_NS));
                    if (idStr != null && !idStr.isEmpty()) {
                        int id = Integer.parseInt(idStr);
                        if (id > maxId) {
                            maxId = id;
                        }
                    }
                } catch (NumberFormatException e) {
                    logger.warn("无法解析批注ID：{}", comment.attributeValue(QName.get("id", W_NS)));
                }
            }

            // 设置计数器为最大ID + 1
            int nextId = maxId + 1;
            commentIdCounter.set(nextId);
            logger.info("【批注冲突检测】检测到{}个现有批注，最大ID={}, 设置新批注ID起始值为{}",
                       comments.size(), maxId, nextId);

        } catch (Exception e) {
            logger.warn("初始化批注ID计数器失败，使用默认值：{}", e.getMessage());
            commentIdCounter.set(1);
        }
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

                // 【新增】如果在锚点段落找不到，尝试在后续段落中搜索
                // 这是为了处理多段落条款的情况（条款标题和条款内容在不同段落）
                if (matchResult == null) {
                    logger.debug("在锚点段落中未找到 targetText，尝试在后续段落中搜索（处理多段落条款）");

                    Element documentElement = documentXml.getRootElement();
                    Element body = documentElement.element(QName.get("body", W_NS));
                    List<Element> allParagraphs = findAllParagraphsRecursive(body);

                    // 找到当前段落在所有段落中的索引
                    int currentParaIndex = -1;
                    for (int i = 0; i < allParagraphs.size(); i++) {
                        if (allParagraphs.get(i) == targetParagraph) {
                            currentParaIndex = i;
                            break;
                        }
                    }

                    if (currentParaIndex >= 0) {
                        // 在后续的 10 个段落内搜索（避免跨越到下一个条款）
                        for (int i = currentParaIndex + 1; i < Math.min(currentParaIndex + 11, allParagraphs.size()); i++) {
                            Element nextPara = allParagraphs.get(i);
                            matchResult = preciseLocator.findTextInParagraph(
                                    nextPara,
                                    issue.getTargetText(),
                                    issue.getMatchPattern() != null ? issue.getMatchPattern() : "EXACT",
                                    issue.getMatchIndex() != null ? issue.getMatchIndex() : 1
                            );

                            if (matchResult != null) {
                                logger.info("【多段落条款】在后续段落 {} 中找到 targetText，从段落 {} 迁移到段落 {}",
                                           i, currentParaIndex, i);
                                targetParagraph = nextPara;  // 更新目标段落
                                startRun = matchResult.getStartRun();
                                endRun = matchResult.getEndRun();
                                break;
                            }
                        }
                    }
                }

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
                    logger.warn("精确文字匹配失败（包括多段落搜索），降级到段落级别批注：targetText={}, matchPattern={}, matchIndex={}",
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
     * 【新版本】anchorId自动级别检测：通过格式判断是条款级还是段落级
     * - 条款级格式: anc-c1-4f21 (不含 -pX-)
     * - 段落级格式: anc-c1-p2-9f4b (含 -pX-)
     * 新版本：支持表格内段落的递归查找
     */
    private Element findTargetParagraph(Document documentXml, ReviewIssue issue, String anchorStrategy) {
        Element documentElement = documentXml.getRootElement();
        Element body = documentElement.element(QName.get("body", W_NS));

        // 使用递归方式获取所有段落（包括表格内的段落）
        List<Element> allParagraphs = findAllParagraphsRecursive(body);

        String anchorId = issue.getAnchorId();
        logger.info("开始查找目标段落：clauseId={}, anchorId={}, 策略={}, 总段落数={} (包含表格内段落)",
                   issue.getClauseId(), anchorId, anchorStrategy, allParagraphs.size());

        // 【关键改进】根据anchorId的格式自动判断是段落级还是条款级
        // 格式说明：
        // - 段落级：anc-c{X}-p{Y}-{hash}  (含有 -pX- 模式)
        // - 条款级：anc-c{X}-{hash}       (不含 -pX- 模式)
        boolean isParagraphLevelAnchor = anchorId != null && anchorId.matches(".*-p\\d+-.*");

        if (isParagraphLevelAnchor) {
            logger.info("【自动检测】anchorId为段落级格式：{}, 直接定位到该段落（无需多段落搜索）", anchorId);
            Element found = findParagraphByAnchor(allParagraphs, anchorId);
            if (found != null) {
                logger.info("✓ 通过段落级anchorId找到目标段落");
                return found;
            }
            logger.warn("✗ 未找到段落级anchorId对应的段落，回退到策略查找");
        } else {
            logger.info("【自动检测】anchorId为条款级格式：{}，使用策略查找", anchorId);
        }

        // 根据策略查找段落
        if ("anchorOnly".equalsIgnoreCase(anchorStrategy)) {
            logger.debug("使用 anchorOnly 策略：仅通过anchorId查找");
            return findParagraphByAnchor(allParagraphs, anchorId);

        } else if ("textFallback".equalsIgnoreCase(anchorStrategy)) {
            logger.debug("使用 textFallback 策略：优先anchorId，失败则用文本匹配");
            Element found = findParagraphByAnchor(allParagraphs, anchorId);
            if (found != null) {
                logger.info("✓ 锚点查找成功");
                return found;
            }

            logger.info("  锚点查找失败，回退到文本匹配");
            return findParagraphByTextMatch(allParagraphs, issue.getClauseId());

        } else {
            // 默认：preferAnchor - 优先锚点，失败则条款ID文本匹配
            logger.debug("使用 preferAnchor 策略（默认）：优先anchorId，失败则用文本匹配");
            Element found = findParagraphByAnchor(allParagraphs, anchorId);
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
     * 递归查找所有指定名称的元素（包括嵌套在其他元素中的）
     */
    private List<Element> findAllElementsRecursive(Element element, QName targetQName) {
        List<Element> results = new ArrayList<>();
        collectElementsRecursive(element, targetQName, results);
        return results;
    }

    /**
     * 递归收集指定元素的辅助方法
     */
    private void collectElementsRecursive(Element element, QName targetQName, List<Element> results) {
        // 如果当前元素匹配，添加到列表
        if (targetQName.equals(element.getQName())) {
            results.add(element);
        }

        // 递归查找子元素
        for (Element child : element.elements()) {
            collectElementsRecursive(child, targetQName, results);
        }
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

            // 4. 实现精确的Run分割：将原Run替换为三个Run

            // 获取原Run的格式属性（runProperties）
            Element originalRunProperties = null;
            List<Element> rpElements = originalRun.elements(QName.get("rPr", W_NS));
            if (!rpElements.isEmpty()) {
                originalRunProperties = rpElements.get(0);
                logger.debug("检测到Run格式属性，将应用到分割后的Run");
            }

            // Step 1: 创建前缀Run（如果有）
            Element prefixRun = null;
            if (!prefix.isEmpty()) {
                prefixRun = new org.dom4j.tree.DefaultElement(QName.get("r", W_NS));
                if (originalRunProperties != null) {
                    // 【修复】使用clone()深度克隆格式属性，先创建rPr容器
                    Element prefixRPr = prefixRun.addElement(QName.get("rPr", W_NS));
                    List<Element> propChildren = originalRunProperties.elements();
                    int copiedCount = 0;
                    for (Element propChild : propChildren) {
                        prefixRPr.add((Element) propChild.clone());  // 使用clone()深度克隆
                        copiedCount++;
                    }
                    logger.debug("前缀Run格式属性克隆完成：复制了 {} 个属性", copiedCount);
                }
                Element prefixText = prefixRun.addElement(QName.get("t", W_NS));
                prefixText.setText(prefix);
                prefixText.addAttribute("xml:space", "preserve");
                paragraph.elements().add(runIndex, prefixRun);
                runIndex++;
                logger.debug("创建前缀Run：'{}'", prefix.length() > 30 ? prefix.substring(0, 30) + "..." : prefix);
            }

            // Step 2: 在匹配部分前插入commentRangeStart
            Element commentRangeStart = new org.dom4j.tree.DefaultElement(QName.get("commentRangeStart", W_NS));
            commentRangeStart.addAttribute(QName.get("id", W_NS), String.valueOf(commentId));
            paragraph.elements().add(runIndex, commentRangeStart);
            runIndex++;  // 现在runIndex指向commentRangeStart，originalRun在runIndex+1
            logger.debug("插入commentRangeStart");

            // Step 3: 创建匹配Run（修改原Run的文本内容）
            // 清空原Run中的文本元素
            List<Element> textElements = originalRun.elements(QName.get("t", W_NS));
            for (Element textElem : new ArrayList<>(textElements)) {
                textElem.getParent().remove(textElem);
            }

            // 在原Run中插入新的文本（匹配部分）
            Element matchedText = originalRun.addElement(QName.get("t", W_NS));
            matchedText.setText(matched);
            matchedText.addAttribute("xml:space", "preserve");
            logger.debug("修改原Run文本为匹配部分：'{}'", matched.length() > 30 ? matched.substring(0, 30) + "..." : matched);

            // Step 4: 在匹配部分后插入commentRangeEnd
            // 【关键修复】在originalRun之后插入commentRangeEnd
            // 现在 originalRun 在 runIndex + 1 的位置
            Element commentRangeEnd = new org.dom4j.tree.DefaultElement(QName.get("commentRangeEnd", W_NS));
            commentRangeEnd.addAttribute(QName.get("id", W_NS), String.valueOf(commentId));
            // originalRun在段落中的位置是runIndex+1，所以commentRangeEnd应该在runIndex+2
            paragraph.elements().add(runIndex + 2, commentRangeEnd);
            runIndex = runIndex + 2;  // 更新runIndex指向commentRangeEnd
            logger.debug("插入commentRangeEnd");

            // Step 5: 创建后缀Run（如果有）
            if (!suffix.isEmpty()) {
                Element suffixRun = new org.dom4j.tree.DefaultElement(QName.get("r", W_NS));
                if (originalRunProperties != null) {
                    // 【修复】使用clone()深度克隆格式属性，先创建rPr容器
                    Element suffixRPr = suffixRun.addElement(QName.get("rPr", W_NS));
                    List<Element> propChildren = originalRunProperties.elements();
                    int copiedCount = 0;
                    for (Element propChild : propChildren) {
                        suffixRPr.add((Element) propChild.clone());  // 使用clone()深度克隆
                        copiedCount++;
                    }
                    logger.debug("后缀Run格式属性克隆完成：复制了 {} 个属性", copiedCount);
                }
                Element suffixText = suffixRun.addElement(QName.get("t", W_NS));
                suffixText.setText(suffix);
                suffixText.addAttribute("xml:space", "preserve");

                // 在commentRangeEnd后插入suffixRun
                paragraph.elements().add(runIndex + 1, suffixRun);
                runIndex++;  // suffixRun占据了一个位置
                logger.debug("创建后缀Run：'{}'", suffix.length() > 30 ? suffix.substring(0, 30) + "..." : suffix);
            }

            // Step 6: 添加批注引用（必须在commentRangeEnd之后）
            Element commentRefRun = new org.dom4j.tree.DefaultElement(QName.get("r", W_NS));
            if (originalRunProperties != null) {
                // 【修复】使用clone()深度克隆格式属性，先创建rPr容器
                Element commentRefRPr = commentRefRun.addElement(QName.get("rPr", W_NS));
                List<Element> propChildren = originalRunProperties.elements();
                int copiedCount = 0;
                for (Element propChild : propChildren) {
                    commentRefRPr.add((Element) propChild.clone());  // 使用clone()深度克隆
                    copiedCount++;
                }
                logger.debug("批注引用Run格式属性克隆完成：复制了 {} 个属性", copiedCount);
            }
            Element commentReference = commentRefRun.addElement(QName.get("commentReference", W_NS));
            commentReference.addAttribute(QName.get("id", W_NS), String.valueOf(commentId));

            // 在commentRangeEnd之后添加commentReference
            // 【关键修复】继续使用runIndex，避免再次调用indexOf()
            paragraph.elements().add(runIndex + 1, commentRefRun);
            logger.debug("插入commentReference");

            logger.info("✓ 精确批注插入完成：commentId={}, 前缀={}, 匹配范围={}-{}, 后缀={}",
                       commentId,
                       prefix.isEmpty() ? "无" : prefix.length(),
                       startOffset, endOffset,
                       suffix.isEmpty() ? "无" : suffix.length());

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
        comment.addAttribute(QName.get("author", W_NS), "AI审查助手");
        comment.addAttribute(QName.get("date", W_NS), new Date().toString());
        comment.addAttribute(QName.get("initials", W_NS), "AI");

        // 【新格式】使用富文本格式，关键词加粗并换行显示
        // 直接在comment下创建多个独立段落，不使用初始段落
        addFormattedCommentContent(comment, issue);

        logger.debug("在comments.xml中添加批注内容：commentId={}, 风险等级={}, 类别={}",
                    commentId, issue.getSeverity(), issue.getCategory());
    }

    /**
     * 添加格式化的批注内容（支持加粗和换行）
     * 【修复】每个部分使用独立段落，关键词和内容在同一段落，不立即换行
     * @param comment 批注元素（w:comment），将在此元素下直接创建段落
     */
    private void addFormattedCommentContent(Element comment, ReviewIssue issue) {
        String severityLabel = getSeverityLabel(issue.getSeverity());
        
        // 1. 风险等级段落（独立段落）
        // 格式：风险等级：高风险（关键词加粗，值不加粗，在同一段落）
        Element riskPara = comment.addElement(QName.get("p", W_NS));
        
        // 关键词（加粗）
        Element run1 = riskPara.addElement(QName.get("r", W_NS));
        Element rPr1 = run1.addElement(QName.get("rPr", W_NS));
        // 添加完整字体设置（确保加粗效果）
        Element fonts1 = rPr1.addElement(QName.get("rFonts", W_NS));
        fonts1.addAttribute(QName.get("ascii", W_NS), "Arial");
        fonts1.addAttribute(QName.get("hAnsi", W_NS), "Arial");
        fonts1.addAttribute(QName.get("eastAsia", W_NS), "Arial");
        fonts1.addAttribute(QName.get("cs", W_NS), "Arial");
        rPr1.addElement(QName.get("b", W_NS)); // 加粗
        rPr1.addElement(QName.get("bCs", W_NS)); // 复杂脚本加粗
        Element sz1 = rPr1.addElement(QName.get("sz", W_NS));
        sz1.addAttribute(QName.get("val", W_NS), "22");
        Element text1 = run1.addElement(QName.get("t", W_NS));
        text1.addAttribute("xml:space", "preserve");
        text1.setText("风险等级：");
        
        // 风险等级值（不加粗，在同一段落）
        Element run2 = riskPara.addElement(QName.get("r", W_NS));
        Element text2 = run2.addElement(QName.get("t", W_NS));
        text2.addAttribute("xml:space", "preserve");
        text2.setText(severityLabel);
        
        // 2. 问题类别段落（独立段落，如果存在）
        if (issue.getCategory() != null) {
            Element categoryPara = comment.addElement(QName.get("p", W_NS));
            
            // 构建完整的类别文本（如"违约条款问题："）
            String categoryText = issue.getCategory();
            if (!categoryText.endsWith("问题") && !categoryText.endsWith("问题：")) {
                categoryText = categoryText + "问题：";
            } else if (!categoryText.endsWith("：")) {
                categoryText = categoryText + "：";
            }
            
            // 关键词（加粗）
            Element run3 = categoryPara.addElement(QName.get("r", W_NS));
            Element rPr3 = run3.addElement(QName.get("rPr", W_NS));
            // 添加完整字体设置（确保加粗效果）
            Element fonts3 = rPr3.addElement(QName.get("rFonts", W_NS));
            fonts3.addAttribute(QName.get("ascii", W_NS), "Arial");
            fonts3.addAttribute(QName.get("hAnsi", W_NS), "Arial");
            fonts3.addAttribute(QName.get("eastAsia", W_NS), "Arial");
            fonts3.addAttribute(QName.get("cs", W_NS), "Arial");
            rPr3.addElement(QName.get("b", W_NS)); // 加粗
            rPr3.addElement(QName.get("bCs", W_NS)); // 复杂脚本加粗
            Element sz3 = rPr3.addElement(QName.get("sz", W_NS));
            sz3.addAttribute(QName.get("val", W_NS), "22");
            Element text3 = run3.addElement(QName.get("t", W_NS));
            text3.addAttribute("xml:space", "preserve");
            text3.setText(categoryText);
            
            // 问题描述（不加粗，在同一段落）
            if (issue.getFinding() != null && !issue.getFinding().isEmpty()) {
                Element run4 = categoryPara.addElement(QName.get("r", W_NS));
                Element text4 = run4.addElement(QName.get("t", W_NS));
                text4.addAttribute("xml:space", "preserve");
                text4.setText(issue.getFinding());
            }
        } else if (issue.getFinding() != null && !issue.getFinding().isEmpty()) {
            // 如果没有类别，单独显示问题描述段落
            Element findingPara = comment.addElement(QName.get("p", W_NS));
            Element run5 = findingPara.addElement(QName.get("r", W_NS));
            Element text5 = run5.addElement(QName.get("t", W_NS));
            text5.addAttribute("xml:space", "preserve");
            text5.setText(issue.getFinding());
        }
        
        // 3. 建议段落（独立段落，如果存在）
        if (issue.getSuggestion() != null && !issue.getSuggestion().isEmpty()) {
            Element suggestionPara = comment.addElement(QName.get("p", W_NS));
            
            // 关键词（加粗）
            Element run6 = suggestionPara.addElement(QName.get("r", W_NS));
            Element rPr6 = run6.addElement(QName.get("rPr", W_NS));
            // 添加完整字体设置（确保加粗效果）
            Element fonts6 = rPr6.addElement(QName.get("rFonts", W_NS));
            fonts6.addAttribute(QName.get("ascii", W_NS), "Arial");
            fonts6.addAttribute(QName.get("hAnsi", W_NS), "Arial");
            fonts6.addAttribute(QName.get("eastAsia", W_NS), "Arial");
            fonts6.addAttribute(QName.get("cs", W_NS), "Arial");
            rPr6.addElement(QName.get("b", W_NS)); // 加粗
            rPr6.addElement(QName.get("bCs", W_NS)); // 复杂脚本加粗
            Element sz6 = rPr6.addElement(QName.get("sz", W_NS));
            sz6.addAttribute(QName.get("val", W_NS), "22");
            Element text6 = run6.addElement(QName.get("t", W_NS));
            text6.addAttribute("xml:space", "preserve");
            text6.setText("建议：");
            
            // 建议内容（不加粗，在同一段落）
            Element run7 = suggestionPara.addElement(QName.get("r", W_NS));
            Element text7 = run7.addElement(QName.get("t", W_NS));
            text7.addAttribute("xml:space", "preserve");
            text7.setText(issue.getSuggestion());
        }
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
        XMLWriter writer = new XMLWriter(outputStream, createOutputFormat());
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
            commentsPart = null; // 统一在下面进行创建
        }

        // 兼容某些实现：getPart() 可能返回 null 而不抛异常
        if (commentsPart == null) {
            commentsPart = opcPackage.createPart(
                commentsPartName,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml"
            );
            logger.info("创建新的comments.xml文件");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLWriter writer = new XMLWriter(outputStream, createOutputFormat());
        writer.write(commentsXml);
        writer.close();

        // 清空现有内容并写入新内容
        try (var partStream = commentsPart.getOutputStream()) {
            partStream.write(outputStream.toByteArray());
            partStream.flush();
        }

        logger.debug("comments.xml保存完成，大小: {} 字节", outputStream.size());
    }

    private void updateDocumentRels(OPCPackage opcPackage) throws Exception {
        PackagePartName relsPartName = PackagingURIHelper.createPartName("/word/_rels/document.xml.rels");

        // 【关键修复1】先检查并确保 rels 文件存在
        PackagePart relsPart;
        try {
            relsPart = opcPackage.getPart(relsPartName);
        } catch (Exception e) {
            // 如果不存在，先创建一个基本的 rels 文件
            logger.info("document.xml.rels不存在，先创建基本文件");
            createBasicDocumentRels(opcPackage);
            relsPart = opcPackage.getPart(relsPartName);
        }

        try {
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
                XMLWriter writer = new XMLWriter(outputStream, createOutputFormat());
                writer.write(relsDoc);
                writer.close();

                // 【关键修复】清空现有内容并写入新内容
                byte[] relsBytes = outputStream.toByteArray();
                try (var partStream = relsPart.getOutputStream()) {
                    partStream.write(relsBytes);
                    partStream.flush();
                }

                logger.debug("添加comments关系到document.xml.rels，大小: {} 字节", relsBytes.length);
                logger.info("【维测】已添加comments关系到document.xml.rels");
                
                // 【关键修复】立即验证写入是否成功
                try {
                    SAXReader verifyReader = new SAXReader();
                    try (java.io.ByteArrayInputStream verifyStream = new java.io.ByteArrayInputStream(relsBytes)) {
                        Document verifyDoc = verifyReader.read(verifyStream);
                        Element verifyRelationships = verifyDoc.getRootElement();
                        logger.debug("【维测】验证读取成功，开始遍历 {} 个关系", verifyRelationships.elements().size());

                        boolean hasCommentsAfter = false;
                        for (Element rel : verifyRelationships.elements()) {
                            String target = rel.attributeValue("Target");
                            String relId = rel.attributeValue("Id");
                            logger.debug("【维测】验证遍历: Id={}, Target={}", relId, target);
                            if ("comments.xml".equals(target)) {
                                hasCommentsAfter = true;
                                logger.info("【维测】✓ 验证通过：comments关系已写入");
                                break;
                            }
                        }
                        if (!hasCommentsAfter) {
                            logger.error("【维测】❌ ERROR: 验证失败：comments关系写入后仍然不存在！");
                            logger.error("【维测】已写入的内容前200字符: {}", new String(relsBytes, 0, Math.min(200, relsBytes.length)));
                        }
                    }
                } catch (Exception verifyEx) {
                    logger.error("【维测】❌ ERROR: 验证时出错: {}", verifyEx.getMessage(), verifyEx);
                }
            } else {
                logger.debug("comments关系已存在，跳过添加");
                logger.info("【维测】comments关系已存在于document.xml.rels中");
            }

            // 【维测】验证关系是否正确写入
            List<Element> allRels = relationships.elements();
            logger.info("【维测】document.xml.rels中总关系数: {}", allRels.size());
            for (Element rel : allRels) {
                String relId = rel.attributeValue("Id");
                String relTarget = rel.attributeValue("Target");
                logger.debug("【维测】关系: Id={}, Target={}", relId, relTarget);
            }

        } catch (Exception e) {
            logger.warn("更新document.xml.rels失败：{}", e.getMessage());
            logger.error("【维测】❌ ERROR: 更新document.xml.rels异常: {}", e.getMessage(), e);

            // 【关键修复】不要完全重新创建rels文件，而是尝试保留原有关系
            logger.warn("尝试恢复document.xml.rels...");
            try {
                recoverOrCreateDocumentRels(opcPackage);
            } catch (Exception recoveryEx) {
                logger.error("恢复失败，最后尝试创建基本关系文件", recoveryEx);
                createBasicDocumentRels(opcPackage);
            }
        }
    }

    /**
     * 尝试恢复或创建document.xml.rels，保留原有关系
     */
    private void recoverOrCreateDocumentRels(OPCPackage opcPackage) throws Exception {
        PackagePartName relsPartName = PackagingURIHelper.createPartName("/word/_rels/document.xml.rels");

        PackagePart relsPart = null;
        Document relsDoc = null;

        try {
            // 尝试获取现有的rels
            relsPart = opcPackage.getPart(relsPartName);
            if (relsPart != null) {
                SAXReader reader = new SAXReader();
                relsDoc = reader.read(relsPart.getInputStream());
                logger.debug("【维测】成功恢复现有document.xml.rels");
            }
        } catch (Exception e) {
            logger.debug("【维测】现有document.xml.rels无法读取，将创建新文件");
        }

        // 如果无法读取现有文件，创建新的
        if (relsDoc == null) {
            relsDoc = DocumentHelper.createDocument();
            Element relationships = relsDoc.addElement("Relationships");
            relationships.addNamespace("", "http://schemas.openxmlformats.org/package/2006/relationships");

            // 【关键修复】添加基本的必需关系
            addBasicRelationships(relationships);

            logger.debug("【维测】创建了新的document.xml.rels");
        }

        // 确保有comments关系
        Element relationships = relsDoc.getRootElement();
        boolean hasCommentsRel = false;
        for (Element rel : relationships.elements()) {
            String target = rel.attributeValue("Target");
            if ("comments.xml".equals(target)) {
                hasCommentsRel = true;
                break;
            }
        }

        if (!hasCommentsRel) {
            Element commentsRel = relationships.addElement("Relationship");
            commentsRel.addAttribute("Id", "rComments");
            commentsRel.addAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments");
            commentsRel.addAttribute("Target", "comments.xml");
            logger.debug("【维测】添加了comments关系");
        }

        // 保存或创建rels文件
        if (relsPart == null) {
            relsPart = opcPackage.createPart(relsPartName, "application/vnd.openxmlformats-package.relationships+xml");
            logger.debug("【维测】创建了新的PackagePart");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLWriter writer = new XMLWriter(outputStream, createOutputFormat());
        writer.write(relsDoc);
        writer.close();

        try (var partStream = relsPart.getOutputStream()) {
            partStream.write(outputStream.toByteArray());
            partStream.flush();
        }

        logger.info("【维测】document.xml.rels恢复/创建成功，大小: {} 字节", outputStream.size());
    }

    /**
     * 添加基本的必需关系
     */
    private void addBasicRelationships(Element relationships) {
        // 添加styles关系（几乎所有Word文档都需要）
        Element stylesRel = relationships.addElement("Relationship");
        stylesRel.addAttribute("Id", "rId1");
        stylesRel.addAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles");
        stylesRel.addAttribute("Target", "styles.xml");
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

        // 【关键修复2】添加所有必需的基本关系
        // 1. styles 关系（几乎所有Word文档都需要）
        Element stylesRel = relationships.addElement("Relationship");
        stylesRel.addAttribute("Id", "rId1");
        stylesRel.addAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles");
        stylesRel.addAttribute("Target", "styles.xml");

        // 2. settings 关系
        Element settingsRel = relationships.addElement("Relationship");
        settingsRel.addAttribute("Id", "rId2");
        settingsRel.addAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings");
        settingsRel.addAttribute("Target", "settings.xml");

        // 3. numbering 关系
        Element numberingRel = relationships.addElement("Relationship");
        numberingRel.addAttribute("Id", "rId3");
        numberingRel.addAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering");
        numberingRel.addAttribute("Target", "numbering.xml");

        // 4. fontTable 关系
        Element fontTableRel = relationships.addElement("Relationship");
        fontTableRel.addAttribute("Id", "rId4");
        fontTableRel.addAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable");
        fontTableRel.addAttribute("Target", "fontTable.xml");

        // 5. theme 关系
        Element themeRel = relationships.addElement("Relationship");
        themeRel.addAttribute("Id", "rId5");
        themeRel.addAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme");
        themeRel.addAttribute("Target", "theme/theme1.xml");

        // 6. comments 关系（批注功能必需）
        Element commentsRel = relationships.addElement("Relationship");
        commentsRel.addAttribute("Id", "rComments");
        commentsRel.addAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments");
        commentsRel.addAttribute("Target", "comments.xml");

        // 【关键修复】检查Part是否已存在，避免重复创建
        PackagePart relsPart;
        try {
            relsPart = opcPackage.getPart(relsPartName);
            logger.debug("document.xml.rels已存在，将覆盖");
        } catch (Exception e) {
            relsPart = opcPackage.createPart(relsPartName, "application/vnd.openxmlformats-package.relationships+xml");
            logger.debug("创建新的document.xml.rels Part");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLWriter writer = new XMLWriter(outputStream, createOutputFormat());
        writer.write(relsDoc);
        writer.close();

        try (var partStream = relsPart.getOutputStream()) {
            partStream.write(outputStream.toByteArray());
            partStream.flush();
        }

        logger.info("【维测】创建/更新document.xml.rels完成，包含{}个关系，大小: {} 字节", 
                    relationships.elements().size(), outputStream.size());
    }

    /**
     * 手动将 document.xml.rels 添加到 ZIP 文件中
     */
    private byte[] manuallyAddRelsToZip(byte[] originalZip, byte[] relsBytes) throws Exception {
        logger.debug("开始手动修复 ZIP 文件：添加 document.xml.rels");
        
        try (java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(originalZip));
             java.io.ByteArrayOutputStream zipOut = new java.io.ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zipOutputStream = new java.util.zip.ZipOutputStream(zipOut)) {
            
            // 先检查是否已有 document.xml.rels
            boolean needsAdd = true;
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if ("word/_rels/document.xml.rels".equals(entry.getName())) {
                    logger.debug("ZIP 中已存在 document.xml.rels，跳过");
                    needsAdd = false;
                }
                
                // 复制其他所有条目
                zipOutputStream.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zipIn.read(buffer)) > 0) {
                        zipOutputStream.write(buffer, 0, len);
                    }
                }
                zipOutputStream.closeEntry();
            }
            
            // 如果需要，添加 document.xml.rels
            if (needsAdd) {
                logger.debug("添加 document.xml.rels 到 ZIP");
                java.util.zip.ZipEntry relsEntry = new java.util.zip.ZipEntry("word/_rels/document.xml.rels");
                zipOutputStream.putNextEntry(relsEntry);
                zipOutputStream.write(relsBytes);
                zipOutputStream.closeEntry();
            }
            
            zipOutputStream.finish();
            
            logger.info("手动修复完成，ZIP 大小: {} 字节 -> {} 字节", 
                       originalZip.length, zipOut.size());
            return zipOut.toByteArray();
        }
    }
}