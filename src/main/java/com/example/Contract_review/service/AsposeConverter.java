package com.example.Contract_review.service;

import com.aspose.words.Document;
import com.aspose.words.SaveFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Aspose Words 文档转换器
 * 
 * 使用Aspose.Words替代LibreOffice进行文档格式转换
 * 主要功能：DOC -> DOCX 转换
 */
@Slf4j
@Component
public class AsposeConverter {

    @Value("${aspose.conversion-timeout-seconds:30}")
    private long conversionTimeoutSeconds;

    /**
     * 将.doc格式转换为.docx格式
     * 
     * @param docBytes 原始.doc文件的字节数组
     * @param originalFilename 原始文件名（用于日志）
     * @return 转换后的.docx文件字节数组
     * @throws IOException 转换失败时抛出
     */
    public byte[] convertDocToDocx(byte[] docBytes, String originalFilename) throws IOException {
        if (docBytes == null || docBytes.length == 0) {
            throw new IllegalArgumentException("待转换的文档内容为空");
        }

        long startTime = System.currentTimeMillis();
        String safeName = sanitizeFilename(originalFilename != null ? originalFilename : "document.doc");
        
        log.info("=== 开始使用Aspose转换文档 ===");
        log.info("原始文件: {}", safeName);
        log.info("输入大小: {} KB ({} bytes)", docBytes.length / 1024, docBytes.length);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(docBytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // 步骤1: 加载.doc文档
            log.debug("步骤1: 加载.doc文档...");
            Document doc = new Document(inputStream);
            log.debug("✓ 文档加载成功，页数: {}", doc.getPageCount());

            // 步骤2: 保存为.docx格式
            log.debug("步骤2: 转换为.docx格式...");
            doc.save(outputStream, SaveFormat.DOCX);
            
            byte[] converted = outputStream.toByteArray();
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // 步骤3: 验证转换结果
            if (converted == null || converted.length == 0) {
                throw new IOException("Aspose转换失败：输出文件为空");
            }

            log.info("✓ Aspose转换成功！");
            log.info("输出大小: {} KB ({} bytes)", converted.length / 1024, converted.length);
            log.info("转换耗时: {} ms", duration);
            log.info("压缩率: {}", 
                String.format("%.2f%%", (1.0 - (double)converted.length / docBytes.length) * 100));
            log.info("=== Aspose转换完成 ===");

            return converted;

        } catch (com.aspose.words.FileCorruptedException e) {
            log.error("✗ 文档损坏或格式不支持: {}", safeName, e);
            throw new IOException("文档损坏或格式不支持: " + e.getMessage(), e);
        } catch (com.aspose.words.UnsupportedFileFormatException e) {
            log.error("✗ 不支持的文件格式: {}", safeName, e);
            throw new IOException("不支持的文件格式: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("✗ Aspose转换失败: {}", safeName, e);
            throw new IOException("Aspose转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理文件名中的特殊字符
     * 
     * @param name 原始文件名
     * @return 清理后的安全文件名
     */
    private String sanitizeFilename(String name) {
        if (name == null || name.isEmpty()) {
            return "document.doc";
        }
        // 移除Windows和Unix文件系统不允许的字符
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 检查Aspose是否可用
     * 
     * @return true表示Aspose已正确配置
     */
    public boolean isAvailable() {
        try {
            // 尝试创建一个空文档来验证Aspose是否可用
            Document testDoc = new Document();
            log.debug("Aspose Words 可用性检查: 成功");
            return true;
        } catch (Exception e) {
            log.warn("Aspose Words 可用性检查: 失败 - {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取Aspose版本信息
     * 
     * @return Aspose Words版本字符串
     */
    public String getVersion() {
        try {
            return com.aspose.words.BuildVersionInfo.getProduct() + " " + 
                   com.aspose.words.BuildVersionInfo.getVersion();
        } catch (Exception e) {
            return "Unknown";
        }
    }
}

