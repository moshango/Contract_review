package com.example.Contract_review.service;

import com.example.Contract_review.model.Clause;
import com.example.Contract_review.model.ParseResult;
import com.example.Contract_review.util.DocxUtils;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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

        List<String> paragraphs;
        String title;
        int wordCount;
        int paragraphCount;

        if (isDocx) {
            // 处理 .docx 文件
            XWPFDocument doc = docxUtils.loadDocx(file.getInputStream());
            paragraphs = docxUtils.parseParagraphs(doc);
            title = docxUtils.extractTitle(doc);
            wordCount = docxUtils.countWords(doc);
            paragraphCount = docxUtils.countParagraphs(doc);
        } else {
            // 处理 .doc 文件
            HWPFDocument doc = docxUtils.loadDoc(file.getInputStream());
            paragraphs = docxUtils.parseDocParagraphs(doc);
            title = paragraphs.isEmpty() ? "未命名文档" : paragraphs.get(0);
            wordCount = paragraphs.stream().mapToInt(String::length).sum();
            paragraphCount = paragraphs.size();
        }

        // 提取条款
        List<Clause> clauses = docxUtils.extractClauses(paragraphs, generateAnchors);

        logger.info("解析完成: title={}, clauses={}, wordCount={}, paragraphCount={}",
                    title, clauses.size(), wordCount, paragraphCount);

        // 构建元数据
        Map<String, Object> meta = new HashMap<>();
        meta.put("wordCount", wordCount);
        meta.put("paragraphCount", paragraphCount);

        return ParseResult.builder()
                .filename(filename)
                .title(title)
                .clauses(clauses)
                .meta(meta)
                .build();
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

        if (!isDocx) {
            throw new IllegalArgumentException("生成带锚点文档仅支持 .docx 格式");
        }

        // 判断是否需要生成锚点
        boolean generateAnchors = "generate".equalsIgnoreCase(anchorMode) ||
                                  "regenerate".equalsIgnoreCase(anchorMode);

        // 加载文档
        byte[] fileBytes = file.getBytes();
        XWPFDocument doc = docxUtils.loadDocx(new ByteArrayInputStream(fileBytes));

        // 解析段落和条款
        List<String> paragraphs = docxUtils.parseParagraphs(doc);
        List<Clause> clauses = docxUtils.extractClauses(paragraphs, generateAnchors);

        // 如果需要生成锚点,插入到文档中
        if (generateAnchors) {
            docxUtils.insertAnchors(doc, clauses);
        }

        // 提取其他信息
        String title = docxUtils.extractTitle(doc);
        int wordCount = docxUtils.countWords(doc);
        int paragraphCount = docxUtils.countParagraphs(doc);

        // 构建元数据
        Map<String, Object> meta = new HashMap<>();
        meta.put("wordCount", wordCount);
        meta.put("paragraphCount", paragraphCount);

        ParseResult parseResult = ParseResult.builder()
                .filename(filename)
                .title(title)
                .clauses(clauses)
                .meta(meta)
                .build();

        // 生成文档字节数组
        byte[] documentBytes = generateAnchors ? docxUtils.writeToBytes(doc) : null;

        logger.info("解析完成并生成带锚点文档: title={}, clauses={}", title, clauses.size());

        return new ParseResultWithDocument(parseResult, documentBytes);
    }

    /**
     * 包含解析结果和文档字节数组的结果类
     */
    public static class ParseResultWithDocument {
        private final ParseResult parseResult;
        private final byte[] documentBytes;

        public ParseResultWithDocument(ParseResult parseResult, byte[] documentBytes) {
            this.parseResult = parseResult;
            this.documentBytes = documentBytes;
        }

        public ParseResult getParseResult() {
            return parseResult;
        }

        public byte[] getDocumentBytes() {
            return documentBytes;
        }
    }
}
