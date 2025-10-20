package com.example.Contract_review.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * 统一处理应用中的异常，提供友好的错误响应
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理Multipart请求格式错误
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> handleMultipartException(MultipartException e) {
        logger.warn("Multipart请求格式错误: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "请求格式错误");
        response.put("message", "该API需要使用 multipart/form-data 格式上传文件");
        response.put("required_format", "multipart/form-data");
        response.put("required_parameters", Map.of(
            "file", "文件参数 (必需)",
            "其他参数", "根据具体API而定"
        ));
        response.put("examples", Map.of(
            "curl_parse", "curl -X POST -F \"file=@contract.docx\" -F \"anchors=none\" -F \"returnMode=json\" http://localhost:8080/api/parse",
            "curl_annotate", "curl -X POST -F \"file=@contract.docx\" -F \"review={\\\"issues\\\":[]}\" http://localhost:8080/api/annotate",
            "javascript", "const formData = new FormData();\nformData.append('file', fileInput.files[0]);\nfetch('/api/parse', { method: 'POST', body: formData })"
        ));
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 处理一般运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        logger.error("运行时异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("error", "服务器内部错误");
        response.put("message", e.getMessage());
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception e) {
        logger.error("未处理的异常: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("error", "服务器错误");
        response.put("message", "处理请求时发生错误，请稍后重试");
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}