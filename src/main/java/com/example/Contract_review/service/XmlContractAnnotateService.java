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
     * 使用XML方式为合同添加批注（新版本 - 高性能版）
     *
     * 【推荐用于】一键审查工作流，直接传递文档字节和 issues 列表
     *
     * 【改进说明】
     * 相比旧版本的优势：
     * 1. 类型安全，编译时检测错误
     * 2. 消除序列化/反序列化的歧义
     * 3. 性能更好，避免不必要的包装
     * 4. 诊断能力强，能检查 anchorId 有效性
     *
     * @param documentBytes 合同文档字节数组（必须是带锚点的文档）
     * @param issues 审查问题列表
     * @param anchorStrategy 锚点策略
     * @param cleanupAnchors 是否清理锚点
     * @return 带批注的文档字节数组
     * @throws IOException 处理失败
     */
    public byte[] annotateContractWithXml(byte[] documentBytes, List<ReviewIssue> issues,
                                         String anchorStrategy, boolean cleanupAnchors) throws IOException {

        logger.info("【新版本】开始XML方式批注处理: issues数量={}, anchorStrategy={}, cleanupAnchors={}",
                   issues != null ? issues.size() : 0, anchorStrategy, cleanupAnchors);

        try {
            // 1. 验证输入参数
            if (documentBytes == null || documentBytes.length == 0) {
                throw new IOException("文档字节数组为空");
            }

            if (issues == null || issues.isEmpty()) {
                logger.warn("没有要添加的审查问题，直接返回原始文档");
                return documentBytes;
            }

            logger.info("✓ 输入验证通过，文档大小: {} 字节, 问题数: {}",
                       documentBytes.length, issues.size());

            // 【关键诊断】检查每个issue的anchorId
            int validAnchorCount = 0;
            int nullAnchorCount = 0;
            for (int i = 0; i < issues.size(); i++) {
                ReviewIssue issue = issues.get(i);
                if (issue.getAnchorId() != null && !issue.getAnchorId().isEmpty()) {
                    validAnchorCount++;
                    logger.debug("[Issue {}] ✓ anchorId={}, clauseId={}, severity={}",
                               i + 1, issue.getAnchorId(), issue.getClauseId(), issue.getSeverity());
                } else {
                    nullAnchorCount++;
                    logger.warn("[Issue {}] ✗ anchorId为NULL，clauseId={}, finding长度={}",
                               i + 1, issue.getClauseId(),
                               issue.getFinding() != null ? issue.getFinding().length() : 0);
                }
            }
            logger.info("✓ 问题诊断：有效anchorId数={}, 缺失anchorId数={}", validAnchorCount, nullAnchorCount);

            // 2. 直接调用XML处理器添加批注
            byte[] annotatedBytes = xmlCommentProcessor.addCommentsToDocx(
                documentBytes, issues, anchorStrategy, cleanupAnchors);

            logger.info("✓ XML批注处理完成，输出文档大小: {} 字节", annotatedBytes.length);
            return annotatedBytes;

        } catch (Exception e) {
            logger.error("XML批注处理失败", e);
            throw new IOException("XML批注处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用XML方式为合同添加批注（旧版本 - 保留用于向后兼容）
     *
     * 【用于】其他控制器中仍使用 MultipartFile 和 JSON 字符串的调用
     *
     * 【说明】
     * 此方法保留用于向后兼容，内部会转换为新版本方法调用
     * 建议逐步迁移调用方使用新版本方法
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
        logger.info("【旧版本兼容】开始XML方式批注处理: filename={}, anchorStrategy={}, cleanupAnchors={}",
                   filename, anchorStrategy, cleanupAnchors);

        try {
            // 1. 验证和解析审查JSON
            logger.info("接收到审查JSON数据");

            if (!validateReviewJson(reviewJson)) {
                throw new IOException("审查JSON格式无效");
            }

            ReviewRequest reviewRequest = objectMapper.readValue(reviewJson, ReviewRequest.class);
            logger.info("JSON解析成功, ReviewRequest对象已创建");

            List<ReviewIssue> issues = reviewRequest.getIssues();
            logger.info("获取到issues列表: 数量={}", issues != null ? issues.size() : 0);

            // 检查每个issue是否包含anchorId
            if (issues != null && !issues.isEmpty()) {
                int validCount = 0;
                int nullCount = 0;
                for (int i = 0; i < issues.size(); i++) {
                    ReviewIssue issue = issues.get(i);
                    if (issue.getAnchorId() != null && !issue.getAnchorId().isEmpty()) {
                        validCount++;
                    } else {
                        nullCount++;
                    }
                }
                logger.info("[旧版本兼容] Issue审查：有效anchorId数={}, 缺失anchorId数={}",
                           validCount, nullCount);
            }

            // 2. 读取原始文档
            byte[] originalBytes = file.getBytes();
            logger.debug("读取原始文档，大小: {} 字节", originalBytes.length);

            // 3. 调用新版本方法进行处理
            return annotateContractWithXml(originalBytes, issues, anchorStrategy, cleanupAnchors);

        } catch (Exception e) {
            logger.error("【旧版本兼容】XML批注处理失败", e);
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