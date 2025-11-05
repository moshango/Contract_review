package com.example.Contract_review.config;

import io.minio.MinioClient;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * MinIO配置类
 * 
 * 负责MinIO客户端的配置和初始化
 */
@Configuration
public class MinioConfig {

    private static final Logger logger = LoggerFactory.getLogger(MinioConfig.class);

    /**
     * MinIO客户端Bean
     */
    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        if (!properties.isEnabled()) {
            logger.info("MinIO服务已禁用");
            return null;
        }

        try {
            logger.info("初始化MinIO客户端: endpoint={}, bucket={}", 
                       properties.getEndpoint(), properties.getBucketName());

            MinioClient client = MinioClient.builder()
                    .endpoint(properties.getEndpoint())
                    .credentials(properties.getAccessKey(), properties.getSecretKey())
                    .build();

            // 测试连接
            testConnection(client, properties);
            
            return client;
        } catch (Exception e) {
            logger.error("MinIO客户端初始化失败", e);
            return null;
        }
    }

    /**
     * 测试MinIO连接
     */
    private void testConnection(MinioClient client, MinioProperties properties) {
        try {
            // 检查bucket是否存在，不存在则创建
            boolean bucketExists = client.bucketExists(
                io.minio.BucketExistsArgs.builder()
                    .bucket(properties.getBucketName())
                    .build()
            );

            if (!bucketExists) {
                logger.info("创建MinIO bucket: {}", properties.getBucketName());
                client.makeBucket(
                    io.minio.MakeBucketArgs.builder()
                        .bucket(properties.getBucketName())
                        .region(properties.getRegion())
                        .build()
                );
                
                // 自动设置bucket为public访问
                setBucketPublicPolicy(client, properties.getBucketName());
            } else {
                // bucket已存在，检查并设置public策略（确保策略正确）
                try {
                    setBucketPublicPolicy(client, properties.getBucketName());
                } catch (Exception e) {
                    logger.warn("设置bucket策略失败（可能已有策略），继续运行: {}", e.getMessage());
                }
            }

            logger.info("✓ MinIO连接测试成功");
        } catch (Exception e) {
            logger.error("MinIO连接测试失败", e);
            throw new RuntimeException("MinIO连接失败", e);
        }
    }

    /**
     * 设置bucket为public访问策略
     */
    private void setBucketPublicPolicy(MinioClient client, String bucketName) {
        try {
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
            
            client.setBucketPolicy(
                io.minio.SetBucketPolicyArgs.builder()
                    .bucket(bucketName)
                    .config(policyJson)
                    .build()
            );
            
            logger.info("✓ MinIO bucket '{}' 已设置为PUBLIC访问", bucketName);
        } catch (Exception e) {
            logger.error("设置bucket策略失败: bucket={}", bucketName, e);
            throw new RuntimeException("设置bucket策略失败: " + e.getMessage(), e);
        }
    }

    /**
     * MinIO配置属性
     */
    @Component
    @ConfigurationProperties(prefix = "minio")
    @Data
    public static class MinioProperties {
        
        /**
         * 是否启用MinIO
         */
        private boolean enabled = true;

        /**
         * MinIO服务端点（用于后端连接MinIO）
         */
        private String endpoint = "http://127.0.0.1:9000";

        /**
         * MinIO公开访问URL（用于生成文件访问链接，通常是通过Nginx反向代理的地址）
         * 如果未配置，则使用endpoint
         */
        private String publicUrl;

        /**
         * 访问密钥
         */
        private String accessKey = "contractadmin";

        /**
         * 秘密密钥
         */
        private String secretKey = "C0ntract!2025#";

        /**
         * 存储桶名称
         */
        private String bucketName = "contract-review";

        /**
         * 区域
         */
        private String region = "us-east-1";

        /**
         * 连接超时时间（毫秒）
         */
        private long connectTimeout = 10000;

        /**
         * 写入超时时间（毫秒）
         */
        private long writeTimeout = 60000;

        /**
         * 读取超时时间（毫秒）
         */
        private long readTimeout = 60000;

        /**
         * 存储路径配置
         */
        private PathConfig path = new PathConfig();

        @Data
        public static class PathConfig {
            /**
             * 合同文件路径
             */
            private String contracts = "contracts";

            /**
             * 报告文件路径
             */
            private String reports = "reports";

            /**
             * 临时文件路径
             */
            private String temp = "temp";
        }
    }
}

