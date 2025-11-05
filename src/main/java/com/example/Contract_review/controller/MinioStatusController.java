package com.example.Contract_review.controller;

import com.example.Contract_review.service.MinioFileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MinIO状态检查控制器
 * 
 * 提供MinIO服务的状态检查和配置信息
 */
@Slf4j
@RestController
@RequestMapping("/api/minio")
public class MinioStatusController {

    @Autowired
    private MinioFileService minioFileService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 检查MinIO服务状态
     */
    @GetMapping("/status")
    public ResponseEntity<?> getMinioStatus() {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            
            boolean enabled = minioFileService.isEnabled();
            response.put("enabled", enabled);
            response.put("status", enabled ? "UP" : "DOWN");
            response.put("config", minioFileService.getConfigInfo());
            response.put("timestamp", System.currentTimeMillis());

            if (enabled) {
                // 测试MinIO连接
                try {
                    // 尝试生成一个测试URL来验证连接
                    String testObjectName = "test/connection-test.txt";
                    boolean exists = minioFileService.fileExists(testObjectName);
                    response.put("connection", "OK");
                    response.put("testResult", "MinIO服务可访问");
                } catch (Exception e) {
                    response.put("connection", "ERROR");
                    response.put("error", e.getMessage());
                    log.warn("MinIO连接测试失败", e);
                }
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取MinIO状态失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("enabled", false);
            error.put("status", "ERROR");
            error.put("error", e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 获取MinIO配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<?> getMinioConfig() {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("config", minioFileService.getConfigInfo());
            response.put("enabled", minioFileService.isEnabled());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取MinIO配置失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(error);
        }
    }
}

