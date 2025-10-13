package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 审查请求模型
 *
 * 用于接收LLM的审查结果JSON,包含多个审查问题
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    /**
     * 审查问题列表
     */
    private List<ReviewIssue> issues;
}
