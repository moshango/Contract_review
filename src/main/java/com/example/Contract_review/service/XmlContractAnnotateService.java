package com.example.Contract_review.service;

import com.example.Contract_review.model.ReviewIssue;
import com.example.Contract_review.model.ReviewRequest;
import com.example.Contract_review.util.WordXmlCommentProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * XML合同批注服务
 *
 * 提供基于纯XML操作的Word文档批注功能
 */
@Service
public class XmlContractAnnotateService {

    private static final Logger logger = LoggerFactory.getLogger(XmlContractAnnotateService.class);

    @Autowired
    private WordXmlCommentProcessor xmlCommentProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 使用XML方式为合同添加批注
     *
     * @param file 合同文件
     * @param reviewJson 审查结果JSON
     * @param anchorStrategy 锚点策略
     * @param cleanupAnchors 是否清理锚点
     * @return 带批注的文档字节数组
     * @throws IOException 处理失败
     */
    public byte[] annotateContractWithXml(MultipartFile file, String reviewJson,
                                         String anchorStrategy, boolean cleanupAnchors) throws IOException {

        String filename = file.getOriginalFilename();
        logger.info("开始XML方式批注处理: filename={}, anchorStrategy={}, cleanupAnchors={}",
                   filename, anchorStrategy, cleanupAnchors);

        try {
            // 1. 验证和解析审查JSON
            logger.info("接收到审查JSON数据: {}", reviewJson);

            if (!validateReviewJson(reviewJson)) {
                throw new IOException("审查JSON格式无效");
            }

            ReviewRequest reviewRequest = objectMapper.readValue(reviewJson, ReviewRequest.class);
            logger.info("JSON解析成功, ReviewRequest对象: {}", reviewRequest);

            List<ReviewIssue> issues = reviewRequest.getIssues();
            logger.info("获取到issues列表: 数量={}", issues != null ? issues.size() : 0);

            // 【重要】检查每个issue是否包含anchorId
            if (issues != null && !issues.isEmpty()) {
                for (int i = 0; i < issues.size(); i++) {
                    ReviewIssue issue = issues.get(i);
                    logger.info("[Issue {}] clauseId={}, anchorId={}, targetText存在={}, 其他字段={severity={}, category={}, finding长度={}}",
                               i + 1,
                               issue.getClauseId(),
                               issue.getAnchorId() != null ? "✓ " + issue.getAnchorId() : "✗ NULL",
                               issue.getTargetText() != null,
                               issue.getSeverity(),
                               issue.getCategory(),
                               issue.getFinding() != null ? issue.getFinding().length() : 0);
                }
            }

            // 2. 读取原始文档
            byte[] originalBytes = file.getBytes();
            logger.debug("读取原始文档，大小: {} 字节", originalBytes.length);

            // 3. 使用XML处理器添加批注
            byte[] annotatedBytes = xmlCommentProcessor.addCommentsToDocx(
                originalBytes, issues, anchorStrategy, cleanupAnchors);

            logger.info("XML批注处理完成，输出文档大小: {} 字节", annotatedBytes.length);
            return annotatedBytes;

        } catch (Exception e) {
            logger.error("XML批注处理失败", e);
            throw new IOException("XML批注处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证审查JSON格式
     *
     * @param reviewJson 审查JSON字符串
     * @return 是否有效
     */
    public boolean validateReviewJson(String reviewJson) {
        if (reviewJson == null || reviewJson.trim().isEmpty()) {
            logger.warn("审查JSON为空");
            return false;
        }

        try {
            ReviewRequest reviewRequest = objectMapper.readValue(reviewJson, ReviewRequest.class);
            List<ReviewIssue> issues = reviewRequest.getIssues();

            logger.debug("JSON验证通过，包含{}个问题", issues != null ? issues.size() : 0);
            return true;

        } catch (Exception e) {
            logger.warn("审查JSON格式验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取审查问题数量
     *
     * @param reviewJson 审查JSON字符串
     * @return 问题数量
     */
    public int getIssueCount(String reviewJson) {
        try {
            if (!validateReviewJson(reviewJson)) {
                return 0;
            }

            ReviewRequest reviewRequest = objectMapper.readValue(reviewJson, ReviewRequest.class);
            List<ReviewIssue> issues = reviewRequest.getIssues();

            return issues != null ? issues.size() : 0;

        } catch (Exception e) {
            logger.warn("获取问题数量失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 构建输出文件名
     *
     * @param originalFilename 原始文件名
     * @return 带批注的文件名
     */
    public String buildOutputFilename(String originalFilename) {
        if (originalFilename == null) {
            return "annotated_contract.docx";
        }

        // 移除扩展名
        String nameWithoutExt = originalFilename;
        if (originalFilename.lastIndexOf('.') > 0) {
            nameWithoutExt = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        }

        return nameWithoutExt + "_xml_annotated.docx";
    }
}