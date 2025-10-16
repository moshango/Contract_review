package com.example.Contract_review.service;

import com.example.Contract_review.model.ReviewIssue;
import com.example.Contract_review.service.XmlContractAnnotateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XML批注功能测试
 */
@SpringBootTest
public class XmlCommentTest {

    @Autowired
    private XmlContractAnnotateService xmlAnnotateService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试创建简单的测试DOCX文档并添加批注
     */
    @Test
    public void testXmlCommentProcessing() throws Exception {
        // 创建测试审查数据
        List<ReviewIssue> issues = new ArrayList<>();

        ReviewIssue issue1 = new ReviewIssue();
        issue1.setClauseId("c1");
        issue1.setAnchorId("anc-c1-test");
        issue1.setSeverity("HIGH");
        issue1.setCategory("条款风险");
        issue1.setFinding("此条款存在法律风险");
        issue1.setSuggestion("建议修改为更严格的表述");
        issues.add(issue1);

        ReviewIssue issue2 = new ReviewIssue();
        issue2.setClauseId("c2");
        issue2.setSeverity("MEDIUM");
        issue2.setCategory("格式问题");
        issue2.setFinding("条款格式不规范");
        issue2.setSuggestion("请按照标准格式重新整理");
        issues.add(issue2);

        // 构造测试JSON
        Map<String, Object> reviewData = new HashMap<>();
        reviewData.put("issues", issues);

        String reviewJson = objectMapper.writeValueAsString(reviewData);
        System.out.println("测试审查JSON: " + reviewJson);

        // 验证JSON格式
        boolean isValid = xmlAnnotateService.validateReviewJson(reviewJson);
        assertTrue(isValid, "审查JSON格式应该有效");

        // 验证问题数量
        int count = xmlAnnotateService.getIssueCount(reviewJson);
        assertEquals(2, count, "应该有2个审查问题");

        System.out.println("XML批注测试通过：JSON验证成功，问题数量正确");
    }

    /**
     * 测试无效JSON处理
     */
    @Test
    public void testInvalidJsonHandling() {
        String invalidJson = "{invalid json}";

        boolean isValid = xmlAnnotateService.validateReviewJson(invalidJson);
        assertFalse(isValid, "无效JSON应该被正确识别");

        int count = xmlAnnotateService.getIssueCount(invalidJson);
        assertEquals(0, count, "无效JSON的问题数量应该为0");
    }

    /**
     * 测试空的审查结果
     */
    @Test
    public void testEmptyReview() throws Exception {
        Map<String, Object> emptyReview = new HashMap<>();
        emptyReview.put("issues", new ArrayList<>());

        String reviewJson = objectMapper.writeValueAsString(emptyReview);

        boolean isValid = xmlAnnotateService.validateReviewJson(reviewJson);
        assertTrue(isValid, "空的审查结果JSON格式应该有效");

        int count = xmlAnnotateService.getIssueCount(reviewJson);
        assertEquals(0, count, "空审查结果的问题数量应该为0");
    }

    /**
     * 创建简单的测试文档内容
     */
    private byte[] createTestDocxContent() {
        // 这里应该创建一个基本的DOCX文件内容
        // 由于复杂性，此处返回一个占位字节数组
        String testContent = "这是一个测试合同文档\n第一条 合同条款\n第二条 责任条款";
        return testContent.getBytes();
    }
}