package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档基本信息
 * 
 * 用于文档预览功能，返回文档的基本元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfo {
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件大小（字节）
     */
    private long fileSize;
    
    /**
     * 页数
     */
    private int pageCount;
    
    /**
     * 文档格式（docx/doc）
     */
    private String format;
    
    /**
     * 是否包含批注
     */
    private boolean hasComments;
    
    /**
     * 文档标题
     */
    private String title;
    
    /**
     * MinIO URL
     */
    private String minioUrl;
}

