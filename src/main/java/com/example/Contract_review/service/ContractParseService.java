package com.example.Contract_review.service;

import com.example.Contract_review.model.Clause;
import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.model.PartyInfo;
import com.example.Contract_review.util.DocxUtils;
import com.example.Contract_review.util.PartyNameExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合同解析服务
 *
 * 负责解析Word合同文档,提取条款结构,生成锚点
 */
@Service
public class ContractParseService {

    private static final Logger logger = LoggerFactory.getLogger(ContractParseService.class);

    @Autowired
    private DocxUtils docxUtils;

    @Autowired
    private ParseResultCache parseResultCache;

    @Autowired
    private AsposeConverter asposeConverter;

    /**
     * 解析合同文档
     *
     * @param file 上传的Word文件
     * @param anchorMode 锚点模式: "none", "generate", "regenerate"
     * @return 解析结果,包含带锚点的文档(如果需要)
     * @throws IOException 文件读取失败
     */
    public ParseResult parseContract(MultipartFile file, String anchorMode) throws IOException {
        logger.info("开始解析合同: filename={}, anchorMode={}", file.getOriginalFilename(), anchorMode);

        String filename = file.getOriginalFilename();
        boolean isDocx = filename != null && filename.toLowerCase().endsWith(".docx");
        boolean isDoc = filename != null && filename.toLowerCase().endsWith(".doc");

        if (!isDocx && !isDoc) {
            throw new IllegalArgumentException("仅支持 .docx 和 .doc 格式文件");
        }

        // 判断是否需要生成锚点
        boolean generateAnchors = "generate".equalsIgnoreCase(anchorMode) ||
                                  "regenerate".equalsIgnoreCase(anchorMode);

        // 【关键修复】先读取文件字节，避免多次读取流导致数据丢失
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            logger.error("无法读取文件字节", e);
            throw e;
        }

        List<Clause> clauses;
        String title;
        int wordCount;
        int paragraphCount;
        byte[] anchoredDocumentBytes = null;  // 【新增】用于缓存
        PartyInfo partyInfo = null;
        String fullContractText = "";
        boolean convertedFromDoc = false;
        String generatedDocxFilename = null;

        if (filename != null) {
            generatedDocxFilename = filename.replaceAll("\\.(?i)(docx|doc)$", "") + "_with_anchors.docx";
        } else {
            generatedDocxFilename = "contract_with_anchors.docx";
        }

        byte[] workingDocBytes = fileBytes;
        XWPFDocument workingDoc = null;

        try {
            if (isDocx) {
                // 处理 .docx 文件
                workingDoc = docxUtils.loadDocx(new ByteArrayInputStream(workingDocBytes));
            } else {
                workingDocBytes = asposeConverter.convertDocToDocx(fileBytes, filename);
                workingDoc = docxUtils.loadDocx(new ByteArrayInputStream(workingDocBytes));
                convertedFromDoc = true;
                logger.info("已使用Aspose将 DOC 文档转换为临时 DOCX 以生成锚点");
            }

            // 【修复】使用真实段落索引方法
            clauses = docxUtils.extractClausesWithCorrectIndex(workingDoc, generateAnchors);

            title = docxUtils.extractTitle(workingDoc);
            wordCount = docxUtils.countWords(workingDoc);
            paragraphCount = docxUtils.countParagraphs(workingDoc);

            if (generateAnchors) {
                anchoredDocumentBytes = docxUtils.writeToBytes(workingDoc);
                logger.info("✓ 带锚点文档已生成，大小: {} 字节", anchoredDocumentBytes != null ? anchoredDocumentBytes.length : 0);
            }

            StringBuilder fullText = new StringBuilder();
            workingDoc.getParagraphs().forEach(p -> fullText.append(p.getText()).append("\n"));
            fullContractText = fullText.toString();
            partyInfo = extractPartyInfoFromDocx(workingDoc);

            if (partyInfo != null && partyInfo.isComplete()) {
                logger.info("✓ 识别到甲方: {}, 乙方: {}", partyInfo.getPartyA(), partyInfo.getPartyB());
            } else {
                logger.warn("⚠ 未能完整识别甲乙方信息");
            }
        } finally {
            if (workingDoc != null) {
                try {
                    workingDoc.close();
                } catch (IOException ignore) {
                }
            }
        }

        // 提取条款（原有逻辑已经整合到上面）
        // List<Clause> clauses = docxUtils.extractClauses(paragraphs, generateAnchors);

        logger.info("解析完成: title={}, clauses={}, wordCount={}, paragraphCount={}",
                    title, clauses.size(), wordCount, paragraphCount);

        // 提取字符串值，用于返回结果
        String partyA = (partyInfo != null) ? partyInfo.getPartyA() : null;
        String partyB = (partyInfo != null) ? partyInfo.getPartyB() : null;
        String partyARoleName = (partyInfo != null) ? partyInfo.getPartyARoleName() : null;
        String partyBRoleName = (partyInfo != null) ? partyInfo.getPartyBRoleName() : null;

        // 【新增】缓存带锚点的文档，生成 parseResultId
        String parseResultId = null;
        if (generateAnchors && anchoredDocumentBytes != null && anchoredDocumentBytes.length > 0) {
            ParseResult tempResult = ParseResult.builder()
                    .filename(filename)
                    .title(title)
                    .partyA(partyA)
                    .partyB(partyB)
                    .partyARoleName(partyARoleName)
                    .partyBRoleName(partyBRoleName)
                    .fullContractText(fullContractText)
                    .clauses(clauses)
                    .build();

            String cacheFilename = generatedDocxFilename != null ? generatedDocxFilename :
                    (filename != null ? filename.replaceAll("\\.(?i)(docx|doc)$", "") + "_with_anchors.docx" : "contract_with_anchors.docx");
            parseResultId = parseResultCache.store(tempResult, anchoredDocumentBytes, cacheFilename);
            logger.info("✓ 带锚点文档已保存到缓存，parseResultId: {}", parseResultId);
        }

        // 构建元数据
        Map<String, Object> meta = new HashMap<>();
        meta.put("wordCount", wordCount);
        meta.put("paragraphCount", paragraphCount);

        ParseResult result = ParseResult.builder()
                .filename(filename)
                .title(title)
                .partyA(partyA)
                .partyB(partyB)
                .partyARoleName(partyARoleName)
                .partyBRoleName(partyBRoleName)
                .fullContractText(fullContractText)
                .clauses(clauses)
                .meta(meta)
                .build();

        // 【新增】将 parseResultId 添加到结果中（需要在 ParseResult 中添加字段）
        // 暂时通过 meta 传递
        if (parseResultId != null) {
            result.getMeta().put("parseResultId", parseResultId);
        }

        result.getMeta().put("convertedFromDoc", convertedFromDoc);
        if (generatedDocxFilename != null) {
            result.getMeta().put("generatedDocxFilename", generatedDocxFilename);
        }

        return result;
    }

    /**
     * 解析合同并生成带锚点的文档
     *
     * @param file 上传的Word文件
     * @param anchorMode 锚点模式
     * @return 包含解析结果和带锚点文档的结果对象
     * @throws IOException 文件读取失败
     */
    public ParseResultWithDocument parseContractWithDocument(MultipartFile file, String anchorMode)
            throws IOException {
        logger.info("开始解析合同并生成带锚点文档: filename={}, anchorMode={}",
                    file.getOriginalFilename(), anchorMode);

        String filename = file.getOriginalFilename();
        boolean isDocx = filename != null && filename.toLowerCase().endsWith(".docx");
        boolean isDoc = filename != null && filename.toLowerCase().endsWith(".doc");

        if (!isDocx && !isDoc) {
            throw new IllegalArgumentException("生成带锚点文档仅支持 .docx 和 .doc 格式");
        }

        boolean convertedFromDoc = false;
        String documentDownloadName;

        if (filename != null) {
            documentDownloadName = filename.replaceAll("\\.(?i)(docx|doc)$", "") + "_with_anchors.docx";
        } else {
            documentDownloadName = "contract_with_anchors.docx";
        }

        // 判断是否需要生成锚点
        boolean generateAnchors = "generate".equalsIgnoreCase(anchorMode) ||
                                  "regenerate".equalsIgnoreCase(anchorMode);

        // 加载文档
        byte[] fileBytes = file.getBytes();
        byte[] workingDocBytes = fileBytes;
        XWPFDocument doc = null;

        try {
            if (isDocx) {
                doc = docxUtils.loadDocx(new ByteArrayInputStream(workingDocBytes));
            } else {
                workingDocBytes = asposeConverter.convertDocToDocx(fileBytes, filename);
                doc = docxUtils.loadDocx(new ByteArrayInputStream(workingDocBytes));
                convertedFromDoc = true;
                logger.info("已使用Aspose将 DOC 文档转换为临时 DOCX 以生成锚点");
            }

            // 【修复】使用真实段落索引方法（解决虚拟索引混乱问题）
            List<Clause> clauses = docxUtils.extractClausesWithCorrectIndex(doc, generateAnchors);

            // 如果需要生成锚点,插入到文档中
            if (generateAnchors) {
                logger.info("【工作流】开始插入锚点到文档中");
                docxUtils.insertAnchors(doc, clauses);
                logger.info("【工作流】锚点插入完成");
            }

            // 提取其他信息
            String title = docxUtils.extractTitle(doc);
            int wordCount = docxUtils.countWords(doc);
            int paragraphCount = docxUtils.countParagraphs(doc);

            // 【新增】提取甲方和乙方名称
            String partyA = null;
            String partyB = null;
            StringBuilder fullText = new StringBuilder();
            doc.getParagraphs().forEach(p -> fullText.append(p.getText()).append("\n"));

            String textForParsing = fullText.length() > 3000 ?
                                   fullText.substring(0, 3000) :
                                   fullText.toString();

            Map<String, String> partyNames = PartyNameExtractor.extractPartyNames(textForParsing);
            if (partyNames != null) {
                partyA = partyNames.get("partyA");
                partyB = partyNames.get("partyB");
                logger.info("✓ 识别到甲方: {}, 乙方: {}", partyA, partyB);
            }

            // 构建元数据
            Map<String, Object> meta = new HashMap<>();
            meta.put("wordCount", wordCount);
            meta.put("paragraphCount", paragraphCount);
            meta.put("anchorSourceFilename", filename);
            meta.put("convertedFromDoc", convertedFromDoc);
            if (convertedFromDoc) {
                meta.put("generatedDocxFilename", documentDownloadName);
            }

            ParseResult parseResult = ParseResult.builder()
                    .filename(filename)
                    .title(title)
                    .partyA(partyA)
                    .partyB(partyB)
                    .clauses(clauses)
                    .meta(meta)
                    .build();

            // 生成文档字节数组
            byte[] documentBytes = null;
            if (generateAnchors) {
                logger.info("【工作流】开始将修改的文档保存为字节数组");
                documentBytes = docxUtils.writeToBytes(doc);
                logger.info("【工作流】文档字节数组生成完成: 大小={} 字节",
                           documentBytes != null ? documentBytes.length : 0);
            } else {
                logger.info("【工作流】未启用锚点生成，documentBytes=null");
            }

            logger.info("解析完成并生成带锚点文档: title={}, clauses={}, documentBytes大小={}, convertedFromDoc={}",
                       title, clauses.size(), documentBytes != null ? documentBytes.length : 0, convertedFromDoc);

            return new ParseResultWithDocument(parseResult, documentBytes, documentDownloadName);
        } finally {
            // 【关键】确保关闭文档，释放资源
            try {
                doc.close();
                logger.debug("【资源管理】XWPFDocument已关闭");
            } catch (IOException e) {
                logger.warn("【资源管理】关闭XWPFDocument时出错", e);
            }
        }
    }

    /**
     * 包含解析结果和文档字节数组的结果类
     */
    public static class ParseResultWithDocument {
        private final ParseResult parseResult;
        private final byte[] documentBytes;
        private final String documentFilename;

        public ParseResultWithDocument(ParseResult parseResult, byte[] documentBytes, String documentFilename) {
            this.parseResult = parseResult;
            this.documentBytes = documentBytes;
            this.documentFilename = documentFilename;
        }

        public ParseResult getParseResult() {
            return parseResult;
        }

        public byte[] getDocumentBytes() {
            return documentBytes;
        }

        public String getDocumentFilename() {
            return documentFilename;
        }
    }

    /**
     * 从 DOCX 文档中识别甲乙方信息
     * 支持多种标签格式：甲方、买方、委托方等
     *
     * @param doc DOCX 文档
     * @return 包含甲乙方信息的 PartyInfo 对象
     */
    private PartyInfo extractPartyInfoFromDocx(XWPFDocument doc) {
        PartyInfo.PartyInfoBuilder builder = PartyInfo.builder();

        // 支持的甲方关键词
        String[] partyAKeywords = {"甲方", "买方", "委托方", "需方", "发包人", "客户", "订购方", "用户"};
        // 支持的乙方关键词
        String[] partyBKeywords = {"乙方", "卖方", "受托方", "供方", "承包人", "服务商", "承接方", "被委托方"};

        // 遍历所有段落寻找甲乙方信息
        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getText().trim();

            if (text.isEmpty()) continue;

            // 检查甲方关键词（仅在未识别时）
            if (builder.build().getPartyA() == null) {
                for (String keyword : partyAKeywords) {
                    if (text.contains(keyword) && text.contains("：")) {
                        // 提取冒号后的内容
                        String[] parts = text.split("[:：]");
                        if (parts.length > 1) {
                            String partyName = parts[1].trim();
                            // 清理括号等特殊符号
                            partyName = partyName.replaceAll("[（(].*?[）)]", "").trim();
                            // 只接受非空的公司名称（不是下划线或纯空白）
                            if (!partyName.isEmpty() && !partyName.matches("^[_\\s]+$")) {
                                builder.partyA(partyName);
                                builder.partyARoleName(keyword);
                                builder.partyALine(text);
                                logger.info("✓ 识别甲方: {}, 标签: {}", partyName, keyword);
                                break;
                            }
                        }
                    }
                }
            }

            // 检查乙方关键词（仅在未识别时）
            if (builder.build().getPartyB() == null) {
                for (String keyword : partyBKeywords) {
                    if (text.contains(keyword) && text.contains("：")) {
                        // 提取冒号后的内容
                        String[] parts = text.split("[:：]");
                        if (parts.length > 1) {
                            String partyName = parts[1].trim();
                            // 清理括号等特殊符号
                            partyName = partyName.replaceAll("[（(].*?[）)]", "").trim();
                            // 只接受非空的公司名称（不是下划线或纯空白）
                            if (!partyName.isEmpty() && !partyName.matches("^[_\\s]+$")) {
                                builder.partyB(partyName);
                                builder.partyBRoleName(keyword);
                                builder.partyBLine(text);
                                logger.info("✓ 识别乙方: {}, 标签: {}", partyName, keyword);
                                break;
                            }
                        }
                    }
                }
            }

            // 早期退出：如果已识别到双方，停止遍历
            if (builder.build().getPartyA() != null && builder.build().getPartyB() != null) {
                logger.info("已识别到甲乙双方，停止继续搜索");
                break;
            }
        }

        return builder.build();
    }

    /**
     * 从段落列表中识别甲乙方信息（用于 DOC 格式）
     *
     * @param paragraphs 段落文本列表
     * @return 包含甲乙方信息的 PartyInfo 对象
     */
    @SuppressWarnings("unused")
    private PartyInfo extractPartyInfoFromParagraphs(List<String> paragraphs) {
        PartyInfo.PartyInfoBuilder builder = PartyInfo.builder();

        // 支持的甲方关键词
        String[] partyAKeywords = {"甲方", "买方", "委托方", "需方", "发包人", "客户", "订购方", "用户"};
        // 支持的乙方关键词
        String[] partyBKeywords = {"乙方", "卖方", "受托方", "供方", "承包人", "服务商", "承接方", "被委托方"};

        // 遍历所有段落寻找甲乙方信息
        for (String text : paragraphs) {
            text = text.trim();

            if (text.isEmpty()) continue;

            // 检查甲方关键词（仅在未识别时）
            if (builder.build().getPartyA() == null) {
                for (String keyword : partyAKeywords) {
                    if (text.contains(keyword) && text.contains("：")) {
                        // 提取冒号后的内容
                        String[] parts = text.split("[:：]");
                        if (parts.length > 1) {
                            String partyName = parts[1].trim();
                            // 清理括号等特殊符号
                            partyName = partyName.replaceAll("[（(].*?[）)]", "").trim();
                            // 只接受非空的公司名称（不是下划线或纯空白）
                            if (!partyName.isEmpty() && !partyName.matches("^[_\\s]+$")) {
                                builder.partyA(partyName);
                                builder.partyARoleName(keyword);
                                builder.partyALine(text);
                                logger.info("✓ 识别甲方: {}, 标签: {}", partyName, keyword);
                                break;
                            }
                        }
                    }
                }
            }

            // 检查乙方关键词（仅在未识别时）
            if (builder.build().getPartyB() == null) {
                for (String keyword : partyBKeywords) {
                    if (text.contains(keyword) && text.contains("：")) {
                        // 提取冒号后的内容
                        String[] parts = text.split("[:：]");
                        if (parts.length > 1) {
                            String partyName = parts[1].trim();
                            // 清理括号等特殊符号
                            partyName = partyName.replaceAll("[（(].*?[）)]", "").trim();
                            // 只接受非空的公司名称（不是下划线或纯空白）
                            if (!partyName.isEmpty() && !partyName.matches("^[_\\s]+$")) {
                                builder.partyB(partyName);
                                builder.partyBRoleName(keyword);
                                builder.partyBLine(text);
                                logger.info("✓ 识别乙方: {}, 标签: {}", partyName, keyword);
                                break;
                            }
                        }
                    }
                }
            }

            // 早期退出：如果已识别到双方，停止遍历
            if (builder.build().getPartyA() != null && builder.build().getPartyB() != null) {
                logger.info("已识别到甲乙双方，停止继续搜索");
                break;
            }
        }

        return builder.build();
    }
}
