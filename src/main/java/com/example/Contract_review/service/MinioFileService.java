package com.example.Contract_review.service;

import com.example.Contract_review.config.MinioConfig;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import io.minio.messages.Item;

/**
 * MinIO文件服务
 * 
 * 提供文件上传、下载、删除等MinIO操作
 */
@Slf4j
@Service
public class MinioFileService {

    @Autowired(required = false)
    private MinioClient minioClient;

    @Autowired
    private MinioConfig.MinioProperties minioProperties;

    /**
     * 检查MinIO服务是否可用
     */
    public boolean isEnabled() {
        return minioProperties.isEnabled() && minioClient != null;
    }

    /**
     * 上传MultipartFile到MinIO
     * 
     * @param file 上传的文件
     * @param objectName 对象名称（包含路径）
     * @return MinIO URL
     */
    public String uploadFile(MultipartFile file, String objectName) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("MinIO服务未启用或未配置");
        }

        try {
            log.info("上传文件到MinIO: objectName={}, size={}", objectName, file.getSize());

            // 确保bucket存在
            ensureBucketExists();

            // 上传文件
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build()
            );

            String url = getFileUrl(objectName);
            log.info("✓ 文件上传成功: {}", url);
            return url;

        } catch (Exception e) {
            log.error("文件上传失败: objectName={}", objectName, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传字节数组到MinIO
     * 
     * @param data 文件数据
     * @param objectName 对象名称（包含路径）
     * @param contentType 内容类型
     * @return MinIO URL
     */
    public String uploadBytes(byte[] data, String objectName, String contentType) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("MinIO服务未启用或未配置");
        }

        try {
            log.info("上传字节数组到MinIO: objectName={}, size={}", objectName, data.length);

            // 确保bucket存在
            ensureBucketExists();

            // 上传字节数组
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build()
            );

            String url = getFileUrl(objectName);
            log.info("✓ 字节数组上传成功: {}", url);
            return url;

        } catch (Exception e) {
            log.error("字节数组上传失败: objectName={}", objectName, e);
            throw new RuntimeException("字节数组上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件URL（公开访问）
     * 
     * @param objectName 对象名称
     * @return 文件访问URL
     */
    public String getFileUrl(String objectName) {
        if (!isEnabled()) {
            return null;
        }

        try {
            // 【修复】返回未编码的URL，由前端统一编码（作为查询参数时）
            // 这样避免双重编码问题
            // 直接使用objectName（不编码），前端会在作为查询参数时编码
            
            // 使用publicUrl（如果配置了反向代理）或endpoint（用于本地连接）
            String baseUrl = minioProperties.getPublicUrl();
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                baseUrl = minioProperties.getEndpoint();
            }
            
            // 对于public bucket，直接返回公开URL（objectName不编码）
            String publicUrl = String.format("%s/%s/%s", 
                baseUrl, 
                minioProperties.getBucketName(), 
                objectName);
            
            log.debug("生成公开文件URL: objectName={}, url={} (未编码，由前端统一编码)", 
                     objectName, publicUrl);
            return publicUrl;

        } catch (Exception e) {
            log.error("生成文件URL失败: objectName={}", objectName, e);
            return null;
        }
    }

    /**
     * 下载文件
     * 
     * @param objectName 对象名称
     * @return 文件字节数组
     */
    public byte[] downloadFile(String objectName) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("MinIO服务未启用或未配置");
        }

        try {
            log.info("从MinIO下载文件: objectName={}", objectName);

            InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build()
            );

            // 读取流到字节数组
            byte[] data = stream.readAllBytes();
            stream.close();

            log.info("✓ 文件下载成功: objectName={}, size={}", objectName, data.length);
            return data;

        } catch (Exception e) {
            log.error("文件下载失败: objectName={}", objectName, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文件
     * 
     * @param objectName 对象名称
     * @return 是否删除成功
     */
    public boolean deleteFile(String objectName) {
        if (!isEnabled()) {
            return false;
        }

        try {
            log.info("从MinIO删除文件: objectName={}", objectName);

            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build()
            );

            log.info("✓ 文件删除成功: objectName={}", objectName);
            return true;

        } catch (Exception e) {
            log.error("文件删除失败: objectName={}", objectName, e);
            return false;
        }
    }

    /**
     * 检查文件是否存在
     * 
     * @param objectName 对象名称
     * @return 是否存在
     */
    public boolean fileExists(String objectName) {
        if (!isEnabled()) {
            return false;
        }

        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成合同文件对象名称
     * 
     * @param originalFilename 原始文件名
     * @param fileType 文件类型（contracts/reports/temp）
     * @return 对象名称
     */
    public String generateObjectName(String originalFilename, String fileType) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        
        // 清理文件名
        String cleanFilename = originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // 构建路径
        String path = getPathByType(fileType);
        return String.format("%s/%s_%s_%s", path, timestamp, uuid, cleanFilename);
    }

    /**
     * 生成审查报告对象名称
     * 
     * @param originalFilename 原始文件名
     * @param reviewType 审查类型
     * @param stance 审查立场
     * @return 对象名称
     */
    public String generateReportObjectName(String originalFilename, String reviewType, String stance) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        
        // 清理文件名
        String baseName = originalFilename.replaceAll("\\.(docx|doc)$", "");
        String cleanBaseName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        // 构建路径
        String path = minioProperties.getPath().getReports();
        return String.format("%s/%s_%s_%s_%s_%s.docx", 
                           path, cleanBaseName, reviewType, stance, timestamp, uuid);
    }

    /**
     * 根据文件类型获取路径
     */
    private String getPathByType(String fileType) {
        switch (fileType.toLowerCase()) {
            case "contracts":
                return minioProperties.getPath().getContracts();
            case "reports":
                return minioProperties.getPath().getReports();
            case "temp":
                return minioProperties.getPath().getTemp();
            default:
                return minioProperties.getPath().getTemp();
        }
    }

    /**
     * 确保bucket存在
     */
    private void ensureBucketExists() throws Exception {
        boolean bucketExists = minioClient.bucketExists(
            BucketExistsArgs.builder()
                .bucket(minioProperties.getBucketName())
                .build()
        );

        if (!bucketExists) {
            log.info("创建MinIO bucket: {}", minioProperties.getBucketName());
            minioClient.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .region(minioProperties.getRegion())
                    .build()
            );
            
            // 自动设置bucket为public访问
            setBucketPublicPolicy();
        } else {
            // bucket已存在，尝试设置public策略（如果失败不影响运行）
            try {
                setBucketPublicPolicy();
            } catch (Exception e) {
                log.debug("设置bucket策略失败（可能已有策略），继续运行: {}", e.getMessage());
            }
        }
    }

    /**
     * 设置bucket为public访问策略
     */
    private void setBucketPublicPolicy() throws Exception {
        try {
            String bucketName = minioProperties.getBucketName();
            String policyJson = String.format(
                "{\n" +
                "  \"Version\": \"2012-10-17\",\n" +
                "  \"Statement\": [\n" +
                "    {\n" +
                "      \"Effect\": \"Allow\",\n" +
                "      \"Principal\": \"*\",\n" +
                "      \"Action\": \"s3:GetObject\",\n" +
                "      \"Resource\": \"arn:aws:s3:::%s/*\"\n" +
                "    }\n" +
                "  ]\n" +
                "}",
                bucketName
            );
            
            minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                    .bucket(bucketName)
                    .config(policyJson)
                    .build()
            );
            
            log.info("✓ MinIO bucket '{}' 已设置为PUBLIC访问", bucketName);
        } catch (Exception e) {
            log.error("设置bucket策略失败: bucket={}", minioProperties.getBucketName(), e);
            throw e;
        }
    }

    /**
     * 获取MinIO配置信息
     */
    public String getConfigInfo() {
        if (!isEnabled()) {
            return "MinIO服务未启用";
        }

        String publicUrl = minioProperties.getPublicUrl();
        if (publicUrl == null || publicUrl.trim().isEmpty()) {
            publicUrl = minioProperties.getEndpoint();
        }

        return String.format("MinIO配置: endpoint=%s, publicUrl=%s, bucket=%s, enabled=%s",
                           minioProperties.getEndpoint(),
                           publicUrl,
                           minioProperties.getBucketName(),
                           minioProperties.isEnabled());
    }

    /**
     * 获取文件列表
     * 
     * @return 文件列表
     */
    public List<Map<String, Object>> listFiles() throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("MinIO服务未启用或未配置");
        }

        try {
            List<Map<String, Object>> files = new ArrayList<>();
            
            Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .recursive(true)
                    .build()
            );

            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("name", item.objectName());
                    fileInfo.put("size", item.size());
                    fileInfo.put("lastModified", item.lastModified());
                    fileInfo.put("etag", item.etag());
                    fileInfo.put("url", getFileUrl(item.objectName()));
                    files.add(fileInfo);
                }
            }

            log.info("获取文件列表成功，共{}个文件", files.size());
            return files;

        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            throw new RuntimeException("获取文件列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取文件详细信息
     * 
     * @param objectName 对象名称
     * @return 文件信息
     */
    public Map<String, Object> getFileInfo(String objectName) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("MinIO服务未启用或未配置");
        }

        try {
            StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build()
            );

            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("name", objectName);
            fileInfo.put("size", stat.size());
            fileInfo.put("lastModified", stat.lastModified());
            fileInfo.put("etag", stat.etag());
            fileInfo.put("contentType", stat.contentType());
            fileInfo.put("url", getFileUrl(objectName));

            return fileInfo;

        } catch (Exception e) {
            log.error("获取文件信息失败: objectName={}", objectName, e);
            throw new RuntimeException("获取文件信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取MinIO端点地址（用于后端连接）
     */
    public String getEndpoint() {
        return minioProperties.getEndpoint();
    }

    /**
     * 获取MinIO公开访问URL（用于生成文件访问链接）
     * 如果配置了publicUrl则返回publicUrl，否则返回endpoint
     */
    public String getPublicUrl() {
        String publicUrl = minioProperties.getPublicUrl();
        if (publicUrl == null || publicUrl.trim().isEmpty()) {
            return minioProperties.getEndpoint();
        }
        return publicUrl;
    }

    /**
     * 获取云桶名称
     */
    public String getBucketName() {
        return minioProperties.getBucketName();
    }
}
