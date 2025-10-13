package com.example.Contract_review.controller;

import com.example.Contract_review.model.ReviewStandard;
import com.example.Contract_review.model.ReviewTemplate;
import com.example.Contract_review.service.ReviewStandardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审查标准管理控制器
 *
 * 提供审查标准和模板的管理接口
 */
@RestController
@RequestMapping("/standards")
public class ReviewStandardController {

    private static final Logger logger = LoggerFactory.getLogger(ReviewStandardController.class);

    @Autowired
    private ReviewStandardService reviewStandardService;

    /**
     * 获取所有审查标准
     */
    @GetMapping
    public ResponseEntity<List<ReviewStandard>> getAllStandards() {
        try {
            List<ReviewStandard> standards = reviewStandardService.getAllStandards();
            return ResponseEntity.ok(standards);
        } catch (Exception e) {
            logger.error("获取审查标准失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据合同类型获取审查标准
     */
    @GetMapping("/contract-type/{contractType}")
    public ResponseEntity<ReviewStandard> getStandardByContractType(
            @PathVariable String contractType) {
        try {
            ReviewStandard standard = reviewStandardService.getStandardByContractType(contractType);
            return ResponseEntity.ok(standard);
        } catch (Exception e) {
            logger.error("获取审查标准失败: contractType={}", contractType, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取所有审查模板
     */
    @GetMapping("/templates")
    public ResponseEntity<List<ReviewTemplate>> getAllTemplates() {
        try {
            List<ReviewTemplate> templates = reviewStandardService.getAllTemplates();
            return ResponseEntity.ok(templates);
        } catch (Exception e) {
            logger.error("获取审查模板失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据合同类型获取审查模板
     */
    @GetMapping("/templates/contract-type/{contractType}")
    public ResponseEntity<ReviewTemplate> getTemplateByContractType(
            @PathVariable String contractType) {
        try {
            ReviewTemplate template = reviewStandardService.getTemplateByContractType(contractType);
            return ResponseEntity.ok(template);
        } catch (Exception e) {
            logger.error("获取审查模板失败: contractType={}", contractType, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 生成审查Prompt
     */
    @PostMapping("/generate-prompt")
    public ResponseEntity<Map<String, String>> generateReviewPrompt(
            @RequestParam(defaultValue = "general") String contractType,
            @RequestBody String contractJson) {
        try {
            String prompt = reviewStandardService.generateReviewPrompt(contractType, contractJson);

            Map<String, String> response = new HashMap<>();
            response.put("contractType", contractType);
            response.put("prompt", prompt);
            response.put("instructions", "请将此Prompt提交给LLM（ChatGPT/Claude/Coze等）进行审查");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("生成审查Prompt失败: contractType={}", contractType, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 保存审查标准
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> saveStandard(@RequestBody ReviewStandard standard) {
        try {
            reviewStandardService.saveStandard(standard);

            Map<String, String> response = new HashMap<>();
            response.put("message", "审查标准保存成功");
            response.put("standardId", standard.getId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("保存审查标准失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 保存审查模板
     */
    @PostMapping("/templates")
    public ResponseEntity<Map<String, String>> saveTemplate(@RequestBody ReviewTemplate template) {
        try {
            reviewStandardService.saveTemplate(template);

            Map<String, String> response = new HashMap<>();
            response.put("message", "审查模板保存成功");
            response.put("templateId", template.getId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("保存审查模板失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取系统支持的合同类型
     */
    @GetMapping("/contract-types")
    public ResponseEntity<List<Map<String, String>>> getSupportedContractTypes() {
        List<Map<String, String>> contractTypes = List.of(
                Map.of("id", "general", "name", "通用合同", "description", "适用于各类通用合同"),
                Map.of("id", "technology", "name", "技术服务合同", "description", "软件开发、技术咨询等"),
                Map.of("id", "purchase", "name", "采购合同", "description", "商品采购、设备采购等"),
                Map.of("id", "service", "name", "服务合同", "description", "外包服务、咨询服务等"),
                Map.of("id", "agency", "name", "代理合同", "description", "销售代理、业务代理等"),
                Map.of("id", "employment", "name", "劳动合同", "description", "员工劳动合同"),
                Map.of("id", "lease", "name", "租赁合同", "description", "房屋租赁、设备租赁等")
        );

        return ResponseEntity.ok(contractTypes);
    }
}