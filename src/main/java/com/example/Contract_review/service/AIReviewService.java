package com.example.Contract_review.service;

import com.example.Contract_review.model.ParseResult;

/**
 * AI审查服务接口
 *
 * 定义AI审查服务的通用接口
 */
public interface AIReviewService {

    /**
     * 使用AI审查合同
     *
     * @param parseResult 解析后的合同内容
     * @param contractType 合同类型
     * @return 审查结果JSON字符串
     * @throws Exception AI调用失败
     */
    String reviewContract(ParseResult parseResult, String contractType) throws Exception;

    /**
     * 检查服务是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 获取服务提供商名称
     *
     * @return 提供商名称
     */
    String getProviderName();
}