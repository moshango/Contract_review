package com.example.Contract_review.controller;

import com.example.Contract_review.service.MinioFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件预览控制器
 * 提供OnlyOffice预览器所需的后端API接口
 */
@RestController
@RequestMapping("/api/preview")
@CrossOrigin(originPatterns = "*")
public class FilePreviewController {

    private static final Logger logger = LoggerFactory.getLogger(FilePreviewController.class);

    @Autowired
    private MinioFileService minioFileService;

    @Autowired(required = false)
    private com.example.Contract_review.service.OnlyOfficeJwtService onlyOfficeJwtService;

    @Value("${onlyoffice.server-url:http://localhost:8082}")
    private String onlyofficeServerUrl;

    @Value("${onlyoffice.backend-url:http://127.0.0.1:8080}")
    private String onlyofficeBackendUrl;

    /**
     * 获取MinIO云桶中的文件列表
     * @return 文件列表信息
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> getFileList() {
        try {
            if (!minioFileService.isEnabled()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "MinIO服务未启用"));
            }

            List<Map<String, Object>> files = minioFileService.listFiles();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("files", files);
            response.put("total", files.size());
            response.put("bucket", minioFileService.getBucketName());
            
            logger.info("获取文件列表成功，共{}个文件", files.size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取文件列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "获取文件列表失败: " + e.getMessage()));
        }
    }

    /**
     * 获取文件的预览URL
     * @param fileName 文件名
     * @return 预览URL信息
     */
    @GetMapping("/url/{fileName}")
    public ResponseEntity<Map<String, Object>> getPreviewUrl(@PathVariable String fileName) {
        try {
            if (!minioFileService.isEnabled()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "MinIO服务未启用"));
            }

            // 检查文件是否存在
            if (!minioFileService.fileExists(fileName)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "文件不存在: " + fileName));
            }

            // 获取文件URL
            String fileUrl = minioFileService.getFileUrl(fileName);
            
            // 获取文件信息
            Map<String, Object> fileInfo = minioFileService.getFileInfo(fileName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileName", fileName);
            response.put("fileUrl", fileUrl);
            response.put("fileInfo", fileInfo);
            
            logger.info("获取文件预览URL成功: {}", fileName);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取文件预览URL失败: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "获取文件预览URL失败: " + e.getMessage()));
        }
    }

    /**
     * 获取OnlyOffice配置信息
     * @return OnlyOffice配置
     */
    @GetMapping("/onlyoffice/config")
    public ResponseEntity<Map<String, Object>> getOnlyOfficeConfig() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("documentServerUrl", onlyofficeServerUrl); // OnlyOffice Document Server地址
            config.put("minioBaseUrl", minioFileService.getEndpoint());
            config.put("bucketName", minioFileService.getBucketName());
            config.put("enabled", minioFileService.isEnabled());
            config.put("jwtEnabled", onlyOfficeJwtService != null && onlyOfficeJwtService.isEnabled());
            
            return ResponseEntity.ok(Map.of("success", true, "config", config));
            
        } catch (Exception e) {
            logger.error("获取OnlyOffice配置失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "获取OnlyOffice配置失败: " + e.getMessage()));
        }
    }

    /**
     * 生成签名的EditorConfig（可选，OnlyOffice开启JWT时使用）
     */
    @GetMapping("/onlyoffice/editor-config")
    public ResponseEntity<Map<String, Object>> getSignedEditorConfig(
            @RequestParam String fileName,
            @RequestParam String fileUrl,
            @RequestParam(defaultValue = "view") String mode) {
        try {
            // 优先直接使用前端传来的公开URL（例如通过Nginx代理的 /minio/...）
            // 先对传入URL做一次“纯净化”：去掉 ?query 和误拼接到路径里的 &originalFileName 之类
            String sanitizedInput = fileUrl;
            int qIdx = sanitizedInput.indexOf('?');
            if (qIdx > 0) sanitizedInput = sanitizedInput.substring(0, qIdx);
            int ampIdx = sanitizedInput.indexOf('&');
            if (ampIdx > 0) sanitizedInput = sanitizedInput.substring(0, ampIdx);

            String finalDocumentUrl = sanitizedInput;
            try {
                java.net.URI u = new java.net.URI(sanitizedInput);
                String path = u.getPath();
                // 清洗异常拼接的参数（例如将 &originalFileName=... 误拼到路径中）
                if (path != null && path.contains("&")) {
                    String cleaned = path.substring(0, path.indexOf('&'));
                    logger.debug("清洗fileUrl路径: rawPath={}, cleanedPath={}", path, cleaned);
                    path = cleaned;
                }
                if (path == null) path = "";
                boolean looksLikePublicMinio = path.startsWith("/minio/") || path.startsWith("/" + minioFileService.getBucketName() + "/");
                if (!looksLikePublicMinio) {
                    // 若不是公开URL，则回退为后端代理（OnlyOffice 一定可达）
                    String bucket = minioFileService.getBucketName();
                    String objectName = null;
                    String marker = "/" + bucket + "/";
                    int idx = fileUrl.indexOf(marker);
                    if (idx >= 0) {
                        objectName = fileUrl.substring(idx + marker.length());
                    }
                    if (objectName == null && path.startsWith("/")) {
                        objectName = path.substring(1);
                    }
                    if (objectName != null && !objectName.isEmpty()) {
                        String encoded = java.net.URLEncoder.encode(objectName, java.nio.charset.StandardCharsets.UTF_8.name())
                                .replace("+", "%20");
                        finalDocumentUrl = onlyofficeBackendUrl + "/api/preview/proxy?fileName=" + encoded;
                        logger.debug("非公开URL，改用后端代理: {}", finalDocumentUrl);
                    } else {
                        logger.debug("无法识别objectName，保留传入URL: {}", finalDocumentUrl);
                    }
                } else {
                    // 公开URL：用原始的 scheme + authority + 清洗后的 path 重建，去除任何错误拼接的参数
                    String rebuilt = u.getScheme() + "://" + u.getAuthority() + path;
                    logger.debug("直接使用公开MinIO URL(清洗后): {}", rebuilt);
                    finalDocumentUrl = rebuilt;
                }
            } catch (Exception ex) {
                logger.warn("公开URL判定失败，保留传入URL: {}", ex.getMessage());
            }

            Map<String, Object> editorConfig = new HashMap<>();
            editorConfig.put("mode", mode);
            editorConfig.put("lang", "zh");
            editorConfig.put("region", "zh-CN");
            editorConfig.put("user", Map.of("id", "user_" + System.currentTimeMillis(), "name", "预览用户"));

            Map<String, Object> document = new HashMap<>();
            document.put("fileType", getFileExtension(fileName));
            // 【修复】使用时间戳+MD5生成文档key，避免OnlyOffice缓存旧文档
            // 每次打开文档时都生成新的key，确保OnlyOffice不使用缓存
            String key;
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
                // 在文件名中加入时间戳，确保每次key都不同
                String keyInput = fileName + "_" + System.currentTimeMillis();
                byte[] digest = md.digest(keyInput.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (byte b : digest) sb.append(String.format("%02x", b));
                key = sb.toString();
                logger.debug("生成OnlyOffice文档key: {}", key);
            } catch (Exception ex) {
                // 如果MD5失败，使用时间戳作为key
                key = String.valueOf(System.currentTimeMillis());
                logger.warn("MD5生成失败，使用时间戳作为key: {}", key);
            }
            document.put("key", key);
            document.put("title", fileName);
            document.put("url", finalDocumentUrl);

            Map<String, Object> cfg = new HashMap<>();
            cfg.put("document", document);
            cfg.put("documentType", "word");
            cfg.put("editorConfig", editorConfig);

            if (onlyOfficeJwtService != null && onlyOfficeJwtService.isEnabled()) {
                String token = onlyOfficeJwtService.sign(cfg);
                cfg.put("token", token);
            }

            return ResponseEntity.ok(Map.of("success", true, "config", cfg));
        } catch (Exception e) {
            logger.error("生成签名EditorConfig失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 代理OnlyOffice健康检查，避免浏览器CORS
     */
    @GetMapping("/onlyoffice/health")
    public ResponseEntity<Map<String, Object>> onlyofficeHealth() {
        try {
            // 使用简单的HTTP客户端检查OnlyOffice健康状态
            java.net.URL url = new java.net.URL(onlyofficeServerUrl + "/healthcheck");
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            boolean ok = responseCode >= 200 && responseCode < 300;
            
            logger.info("OnlyOffice健康检查: url={}, responseCode={}, ok={}", 
                       onlyofficeServerUrl + "/healthcheck", responseCode, ok);
            
            return ResponseEntity.ok(Map.of("success", ok, "responseCode", responseCode));
            
        } catch (Exception e) {
            logger.warn("OnlyOffice健康检查失败: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 检查文件是否支持OnlyOffice预览
     * @param fileName 文件名
     * @return 是否支持预览
     */
    @GetMapping("/supported")
    public ResponseEntity<Map<String, Object>> isFileSupported(@RequestParam String fileName) {
        try {
            // URL解码文件名
            String decodedFileName = java.net.URLDecoder.decode(fileName, "UTF-8");
            String extension = getFileExtension(decodedFileName).toLowerCase();
            
            // OnlyOffice支持的文件格式
            List<String> supportedFormats = List.of(
                "docx", "doc", "xlsx", "xls", "pptx", "ppt", 
                "pdf", "txt", "rtf", "odt", "ods", "odp"
            );
            
            boolean supported = supportedFormats.contains(extension);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileName", decodedFileName);
            response.put("extension", extension);
            response.put("supported", supported);
            response.put("supportedFormats", supportedFormats);
            
            logger.info("检查文件支持状态: fileName={}, extension={}, supported={}", 
                       decodedFileName, extension, supported);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("检查文件支持状态失败: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "检查文件支持状态失败: " + e.getMessage()));
        }
    }

    /**
     * 代理文档访问，解决OnlyOffice无法直接访问MinIO的问题
     * @param fileName 文件名
     * @return 文档内容
     */
    @GetMapping("/proxy/**")
    public ResponseEntity<byte[]> proxyDocument(HttpServletRequest request) {
        String decodedFileName = null;
        try {
            // 从请求路径中提取文件名
            String requestPath = request.getRequestURI();
            String fileName = requestPath.substring("/api/preview/proxy/".length());
            
            // URL解码文件名
            decodedFileName = java.net.URLDecoder.decode(fileName, "UTF-8");
            
            // 从MinIO下载文件
            byte[] fileData = minioFileService.downloadFile(decodedFileName);
            
            logger.info("代理文档访问: fileName={}, size={}", 
                       decodedFileName, fileData.length);
            
            return buildRangeAwareResponse(fileData, decodedFileName);
            
        } catch (Exception e) {
            logger.error("代理文档访问失败: fileName={}", decodedFileName, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * 使用查询参数方式代理文档访问，避免编码斜杠问题
     */
    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxyDocumentByQuery(@RequestParam String fileName) {
        try {
            String decodedFileName = java.net.URLDecoder.decode(fileName, "UTF-8");
            logger.debug("【诊断】代理下载: fileName={}", decodedFileName);
            
            byte[] fileData = minioFileService.downloadFile(decodedFileName);
            
            // 【诊断】验证下载的文档是否包含批注
            boolean hasComments = validateDocHasComments(fileData);
            logger.info("【诊断】代理下载文档包含批注: {}, size={}字节", hasComments, fileData.length);
            
            if (!hasComments) {
                logger.error("⚠️ 【严重警告】代理下载的文档不包含批注！fileName={}", decodedFileName);
            }
            
            // 【诊断】计算MD5
            try {
                java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
                byte[] hash = md5.digest(fileData);
                String md5Str = bytesToHex(hash);
                logger.debug("【诊断】代理下载文档MD5: {}", md5Str);
            } catch (Exception e) {
                logger.warn("【诊断】无法计算MD5: {}", e.getMessage());
            }
            
            return buildRangeAwareResponse(fileData, decodedFileName);
        } catch (Exception e) {
            logger.error("代理文档访问失败: fileName={}", fileName, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * 处理 HEAD 请求（path 方式）
     */
    @RequestMapping(value = "/proxy/**", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headProxyPath(HttpServletRequest request) {
        String decodedFileName = null;
        try {
            String requestPath = request.getRequestURI();
            String fileName = requestPath.substring("/api/preview/proxy/".length());
            decodedFileName = java.net.URLDecoder.decode(fileName, "UTF-8");
            byte[] fileData = minioFileService.downloadFile(decodedFileName);
            return buildHeadResponse(fileData.length, decodedFileName);
        } catch (Exception e) {
            logger.error("HEAD 代理失败: fileName={}", decodedFileName, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * 处理 HEAD 请求（query 方式）
     */
    @RequestMapping(value = "/proxy", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headProxyQuery(@RequestParam String fileName) {
        String decodedFileName = null;
        try {
            decodedFileName = java.net.URLDecoder.decode(fileName, "UTF-8");
            byte[] fileData = minioFileService.downloadFile(decodedFileName);
            return buildHeadResponse(fileData.length, decodedFileName);
        } catch (Exception e) {
            logger.error("HEAD 代理失败: fileName={}", decodedFileName, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * 构建支持 Range 的响应，满足 OnlyOffice 取流要求
     */
    private ResponseEntity<byte[]> buildRangeAwareResponse(byte[] fullData, String fullFileName) {
        try {
            String contentType = getContentType(fullFileName);

            String displayName = fullFileName;
            int lastSlash = fullFileName.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < fullFileName.length() - 1) {
                displayName = fullFileName.substring(lastSlash + 1);
            }

            long fileLength = fullData.length;
            long start = 0;
            long end = fileLength - 1;

            String rangeHeader = null;
            try {
                rangeHeader = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes ?
                        ((org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest().getHeader("Range") : null;
            } catch (Exception ignore) {}

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String[] parts = rangeHeader.substring(6).split("-");
                try {
                    if (!parts[0].isEmpty()) start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) end = Long.parseLong(parts[1]);
                    if (end >= fileLength) end = fileLength - 1;
                    if (start < 0 || start > end) { start = 0; end = fileLength - 1; }
                } catch (NumberFormatException ignore) { start = 0; end = fileLength - 1; }

                int sliceLen = (int) (end - start + 1);
                byte[] slice = java.util.Arrays.copyOfRange(fullData, (int) start, (int) (end + 1));
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(sliceLen))
                        .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileLength))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + displayName + "\"")
                        .header("Cache-Control", "no-cache, no-store, must-revalidate")
                        .header("Pragma", "no-cache")
                        .header("Expires", "0")
                        .body(slice);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileLength))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + displayName + "\"")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .body(fullData);
        } catch (Exception e) {
            logger.error("构建Range响应失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<Void> buildHeadResponse(long contentLength, String fileName) {
        String contentType = getContentType(fileName);
        String displayName = fileName;
        int lastSlash = fileName.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < fileName.length() - 1) {
            displayName = fileName.substring(lastSlash + 1);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + displayName + "\"")
                .build();
    }

    /**
     * 根据文件扩展名获取Content-Type
     */
    private String getContentType(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        switch (extension) {
            case "docx":
                // OnlyOffice需要正确的MIME类型来识别文件格式
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc":
                return "application/msword";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls":
                return "application/vnd.ms-excel";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pdf":
                return "application/pdf";
            case "txt":
                return "text/plain; charset=utf-8";
            case "rtf":
                return "application/rtf";
            case "odt":
                return "application/vnd.oasis.opendocument.text";
            case "ods":
                return "application/vnd.oasis.opendocument.spreadsheet";
            case "odp":
                return "application/vnd.oasis.opendocument.presentation";
            default:
                // 未知类型使用octet-stream作为兜底
                return "application/octet-stream";
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx == -1 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1);
    }
    
    /**
     * 验证DOCX文档是否包含批注文件
     */
    private boolean validateDocHasComments(byte[] docBytes) {
        try {
            java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(docBytes));
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/comments.xml".equals(entry.getName())) {
                    zip.close();
                    return true;
                }
                zip.closeEntry();
            }
            zip.close();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 辅助方法：字节数组转十六进制
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
