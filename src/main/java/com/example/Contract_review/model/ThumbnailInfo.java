package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缩略图信息
 * 
 * 用于文档预览功能，返回单页缩略图数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThumbnailInfo {
    
    /**
     * 页码（从1开始）
     */
    private int pageNumber;
    
    /**
     * 缩略图Base64编码
     */
    private String thumbnailBase64;
    
    /**
     * 图片宽度（像素）
     */
    private int width;
    
    /**
     * 图片高度（像素）
     */
    private int height;
    
    /**
     * 缩略图大小（字节）
     */
    private long size;
}

