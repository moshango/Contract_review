package com.example.Contract_review.controller;

import com.example.Contract_review.model.DocumentInfo;
import com.example.Contract_review.model.ThumbnailInfo;
import com.example.Contract_review.service.DocumentViewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文档查看控制器
 * 
 * 提供基于Aspose的文档预览API
 * 替代OnlyOffice，提供更轻量级的预览方案
 */
@Slf4j
@RestController
@RequestMapping("/api/document-view")
public class DocumentViewController {
    
    @Autowired
    private DocumentViewService documentViewService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * 获取文档基本信息
     * 
     * GET /api/document-view/info?fileUrl={minioUrl}
     * 
     * @param fileUrl MinIO文件URL（URL编码）
     * @return 文档信息
     */
    @GetMapping("/info")
    public ResponseEntity<?> getDocumentInfo(@RequestParam("fileUrl") String fileUrl) {
        try {
            // URL解码
            String minioUrl = URLDecoder.decode(fileUrl, StandardCharsets.UTF_8);
            log.info("=== 获取文档信息 ===");
            log.info("MinIO URL: {}", minioUrl);
            
            DocumentInfo info = documentViewService.getDocumentInfo(minioUrl);
            
            return ResponseEntity.ok(info);
            
        } catch (IllegalArgumentException e) {
            log.error("参数错误", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("获取文档信息失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "获取文档信息失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * 获取所有页的缩略图
     * 
     * GET /api/document-view/thumbnails?fileUrl={minioUrl}
     * 
     * @param fileUrl MinIO文件URL（URL编码）
     * @return 缩略图列表
     */
    @GetMapping("/thumbnails")
    public ResponseEntity<?> getAllThumbnails(@RequestParam("fileUrl") String fileUrl) {
        try {
            String minioUrl = URLDecoder.decode(fileUrl, StandardCharsets.UTF_8);
            log.info("=== 获取所有缩略图 ===");
            log.info("MinIO URL: {}", minioUrl);
            
            List<ThumbnailInfo> thumbnails = documentViewService.getAllThumbnails(minioUrl);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("pageCount", thumbnails.size());
            
            ArrayNode thumbArray = response.putArray("thumbnails");
            for (ThumbnailInfo thumb : thumbnails) {
                ObjectNode thumbNode = thumbArray.addObject();
                thumbNode.put("pageNumber", thumb.getPageNumber());
                thumbNode.put("thumbnailBase64", thumb.getThumbnailBase64());
                thumbNode.put("width", thumb.getWidth());
                thumbNode.put("height", thumb.getHeight());
                thumbNode.put("size", thumb.getSize());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取缩略图失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "获取缩略图失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * 获取单页缩略图
     * 
     * GET /api/document-view/thumbnails/{pageNum}?fileUrl={minioUrl}
     * 
     * @param fileUrl MinIO文件URL（URL编码）
     * @param pageNum 页码（从1开始）
     * @return 单页缩略图
     */
    @GetMapping("/thumbnails/{pageNum}")
    public ResponseEntity<?> getPageThumbnail(
            @RequestParam("fileUrl") String fileUrl,
            @PathVariable("pageNum") int pageNum) {
        try {
            String minioUrl = URLDecoder.decode(fileUrl, StandardCharsets.UTF_8);
            log.info("=== 获取单页缩略图 ===");
            log.info("MinIO URL: {}, 页码: {}", minioUrl, pageNum);
            
            ThumbnailInfo thumbnail = documentViewService.getPageThumbnail(minioUrl, pageNum);
            
            return ResponseEntity.ok(thumbnail);
            
        } catch (IllegalArgumentException e) {
            log.error("页码错误", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("获取缩略图失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "获取缩略图失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * 获取单页SVG
     * 
     * GET /api/document-view/pages/{pageNum}?fileUrl={minioUrl}
     * 
     * @param fileUrl MinIO文件URL（URL编码）
     * @param pageNum 页码（从1开始）
     * @return SVG文本
     */
    @GetMapping("/pages/{pageNum}")
    public ResponseEntity<?> getPageSvg(
            @RequestParam("fileUrl") String fileUrl,
            @PathVariable("pageNum") int pageNum) {
        try {
            String minioUrl = URLDecoder.decode(fileUrl, StandardCharsets.UTF_8);
            log.info("=== 获取页面SVG ===");
            log.info("MinIO URL: {}, 页码: {}", minioUrl, pageNum);
            
            String svg = documentViewService.getPageSvg(minioUrl, pageNum);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/svg+xml"))
                    .body(svg);
            
        } catch (IllegalArgumentException e) {
            log.error("页码错误", e);
            return ResponseEntity.badRequest()
                    .body("页码错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("获取SVG失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取SVG失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量获取页面SVG
     * 
     * GET /api/document-view/pages?fileUrl={minioUrl}&start=1&end=3
     * 
     * @param fileUrl MinIO文件URL（URL编码）
     * @param start 起始页（默认1）
     * @param end 结束页（默认与start相同）
     * @return SVG列表
     */
    @GetMapping("/pages")
    public ResponseEntity<?> getPagesSvg(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "start", defaultValue = "1") int start,
            @RequestParam(value = "end", required = false) Integer end) {
        try {
            String minioUrl = URLDecoder.decode(fileUrl, StandardCharsets.UTF_8);
            
            // 如果未指定end，则与start相同
            if (end == null) {
                end = start;
            }
            
            log.info("=== 批量获取SVG ===");
            log.info("MinIO URL: {}, 页码范围: {}-{}", minioUrl, start, end);
            
            List<String> svgList = documentViewService.getPagesSvg(minioUrl, start, end);
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("start", start);
            response.put("end", end);
            response.put("count", svgList.size());
            
            ArrayNode svgArray = response.putArray("pages");
            for (int i = 0; i < svgList.size(); i++) {
                ObjectNode pageNode = svgArray.addObject();
                pageNode.put("pageNumber", start + i);
                pageNode.put("svg", svgList.get(i));
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("批量获取SVG失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "批量获取SVG失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * 清理缓存
     * 
     * DELETE /api/document-view/cache?fileUrl={minioUrl}
     * 
     * @param fileUrl MinIO文件URL（可选，不传则清理全部）
     * @return 清理结果
     */
    @DeleteMapping("/cache")
    public ResponseEntity<?> clearCache(
            @RequestParam(value = "fileUrl", required = false) String fileUrl) {
        try {
            if (fileUrl != null && !fileUrl.isEmpty()) {
                String minioUrl = URLDecoder.decode(fileUrl, StandardCharsets.UTF_8);
                documentViewService.clearDocumentCache(minioUrl);
                log.info("已清理指定文档缓存: {}", minioUrl);
            } else {
                documentViewService.clearCache();
                log.info("已清理所有文档缓存");
            }
            
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.put("message", "缓存已清理");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("清理缓存失败", e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "清理缓存失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}

