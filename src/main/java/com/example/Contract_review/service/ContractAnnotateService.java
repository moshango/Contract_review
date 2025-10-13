package com.example.Contract_review.service;

import com.example.Contract_review.model.Clause;
import com.example.Contract_review.model.ReviewIssue;
import com.example.Contract_review.model.ReviewRequest;
import com.example.Contract_review.util.DocxUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 合同批注服务
 *
 * 负责根据审查结果在合同中插入批注
 */
@Service
public class ContractAnnotateService {

    private static final Logger logger = LoggerFactory.getLogger(ContractAnnotateService.class);

    @Autowired
    private DocxUtils docxUtils;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 为合同添加批注
     *
     * @param file 原始合同文件
     * @param reviewJson 审查结果JSON字符串
     * @param anchorStrategy 锚点定位策略: "preferAnchor", "anchorOnly", "textFallback"
     * @param cleanupAnchors 是否清理锚点
     * @return 带批注的文档字节数组
     * @throws IOException 文件处理失败
     */
    public byte[] annotateContract(MultipartFile file, String reviewJson,
                                   String anchorStrategy, boolean cleanupAnchors) throws IOException {
        logger.info("开始为合同添加批注: filename={}, anchorStrategy={}, cleanupAnchors={}",
                    file.getOriginalFilename(), anchorStrategy, cleanupAnchors);

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("批注功能仅支持 .docx 格式文件");
        }

        // 解析审查结果JSON
        logger.info("接收到的审查JSON内容: {}", reviewJson);

        ReviewRequest reviewRequest;
        List<ReviewIssue> issues;

        try {
            reviewRequest = objectMapper.readValue(reviewJson, ReviewRequest.class);
            logger.info("JSON解析成功, ReviewRequest对象: {}", reviewRequest);

            issues = reviewRequest.getIssues();
            logger.info("提取的issues列表: {}, 数量: {}", issues, issues != null ? issues.size() : 0);

            if (issues == null || issues.isEmpty()) {
                logger.warn("审查结果为空,无批注可添加. reviewJson内容: {}", reviewJson);
                return file.getBytes();
            }
        } catch (Exception e) {
            logger.error("JSON解析失败: {}, 原始JSON: {}", e.getMessage(), reviewJson, e);
            throw new IOException("审查结果JSON格式错误: " + e.getMessage(), e);
        }

        // 加载文档
        XWPFDocument doc = docxUtils.loadDocx(file.getInputStream());

        // 解析条款(用于fallback定位)
        List<String> paragraphs = docxUtils.parseParagraphs(doc);
        List<Clause> clauses = docxUtils.extractClauses(paragraphs, false);

        // 为每个问题插入批注
        int successCount = 0;
        for (ReviewIssue issue : issues) {
            logger.info("处理批注: clauseId={}, anchorId={}, category={}",
                       issue.getClauseId(), issue.getAnchorId(), issue.getCategory());

            int paraIndex = findParagraphIndex(doc, clauses, issue, anchorStrategy);

            if (paraIndex >= 0) {
                String commentText = formatComment(issue);
                docxUtils.insertComment(doc, paraIndex, commentText);
                successCount++;
                logger.info("成功插入批注: clauseId={}, paraIndex={}", issue.getClauseId(), paraIndex);
            } else {
                logger.warn("无法找到批注位置: clauseId={}, anchorId={}, finding={}",
                           issue.getClauseId(), issue.getAnchorId(), issue.getFinding());

                // 如果找不到精确位置,尝试插入到文档末尾
                if (!"anchorOnly".equalsIgnoreCase(anchorStrategy)) {
                    List<XWPFParagraph> allParas = doc.getParagraphs();
                    if (!allParas.isEmpty()) {
                        int lastIndex = allParas.size() - 1;
                        String commentText = formatComment(issue) + " (注: 无法精确定位,已添加到文档末尾)";
                        docxUtils.insertComment(doc, lastIndex, commentText);
                        successCount++;
                        logger.info("批注已添加到文档末尾: clauseId={}", issue.getClauseId());
                    }
                }
            }
        }

        // 清理锚点(如果需要)
        if (cleanupAnchors) {
            docxUtils.cleanupAnchors(doc);
            logger.info("已清理文档中的锚点");
        }

        // 生成文档字节数组
        byte[] result = docxUtils.writeToBytes(doc);
        logger.info("批注完成: issues={}, successCount={}, cleanupAnchors={}",
                   issues.size(), successCount, cleanupAnchors);

        return result;
    }

    /**
     * 根据策略查找段落索引
     *
     * @param doc XWPFDocument对象
     * @param clauses 条款列表
     * @param issue 审查问题
     * @param strategy 定位策略
     * @return 段落索引,未找到返回-1
     */
    private int findParagraphIndex(XWPFDocument doc, List<Clause> clauses,
                                   ReviewIssue issue, String strategy) {
        if ("anchorOnly".equalsIgnoreCase(strategy)) {
            // 仅使用锚点定位
            if (issue.getAnchorId() != null) {
                return docxUtils.findParagraphByAnchor(doc, issue.getAnchorId());
            }
            return -1;
        } else if ("textFallback".equalsIgnoreCase(strategy)) {
            // 优先锚点,然后条款ID,最后文本匹配
            if (issue.getAnchorId() != null) {
                int index = docxUtils.findParagraphByAnchor(doc, issue.getAnchorId());
                if (index >= 0) return index;
            }

            if (issue.getClauseId() != null) {
                int index = docxUtils.findParagraphByClauseId(clauses, issue.getClauseId());
                if (index >= 0) return index;
            }

            // TODO: 实现文本匹配fallback
            return -1;
        } else {
            // 默认策略: preferAnchor
            // 优先使用锚点,否则使用条款ID
            if (issue.getAnchorId() != null) {
                int index = docxUtils.findParagraphByAnchor(doc, issue.getAnchorId());
                if (index >= 0) return index;
            }

            if (issue.getClauseId() != null) {
                return docxUtils.findParagraphByClauseId(clauses, issue.getClauseId());
            }

            return -1;
        }
    }

    /**
     * 格式化批注内容
     *
     * @param issue 审查问题
     * @return 格式化的批注文本
     */
    private String formatComment(ReviewIssue issue) {
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
     *
     * @param severity 风险等级
     * @return 中文标签
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
}
