package com.example.Contract_review.service;

import com.aspose.words.*;
import com.example.Contract_review.model.DocumentInfo;
import com.example.Contract_review.model.ThumbnailInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档查看服务
 * 
 * 使用Aspose.Words提供文档预览功能
 * - 生成文档缩略图（PNG, Base64）
 * - 导出页面为SVG矢量图
 * - 获取文档基本信息
 */
@Slf4j
@Service
public class DocumentViewService {
    
    @Autowired
    private MinioFileService minioFileService;
    
    // 文档字节数组缓存（避免重复下载）
    // 注意：不能直接缓存Document对象，Aspose Document对象在缓存后会出现状态问题
    private final ConcurrentHashMap<String, byte[]> documentBytesCache = new ConcurrentHashMap<>();
    
    // 缩略图缓存
    private final ConcurrentHashMap<String, String> thumbnailCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间（5分钟）
    private static final long CACHE_EXPIRE_MS = 5 * 60 * 1000;
    
    /**
     * 从MinIO获取文档字节数组（带缓存）
     * 
     * @param minioUrl MinIO文件URL
     * @return 文档字节数组
     */
    private byte[] getDocumentBytes(String minioUrl) throws Exception {
        String cacheKey = minioUrl;
        
        // 检查缓存
        if (documentBytesCache.containsKey(cacheKey)) {
            log.debug("从缓存获取文档字节数组: {}", minioUrl);
            return documentBytesCache.get(cacheKey);
        }
        
        log.info("从MinIO下载文档: {}", minioUrl);
        
        // 从MinIO URL提取objectName
        String objectName = extractObjectName(minioUrl);
        
        // 从MinIO下载文件
        byte[] fileBytes = minioFileService.downloadFile(objectName);
        
        if (fileBytes == null || fileBytes.length == 0) {
            throw new RuntimeException("文件下载失败或文件为空");
        }
        
        log.info("文件下载成功，大小: {} KB", fileBytes.length / 1024);
        
        // 放入缓存
        documentBytesCache.put(cacheKey, fileBytes);
        
        return fileBytes;
    }
    
    /**
     * 从MinIO加载文档（每次创建新的Document对象）
     * 
     * @param minioUrl MinIO文件URL
     * @return Aspose Document对象（新创建）
     */
    private Document loadDocumentFromMinio(String minioUrl) throws Exception {
        // 获取文档字节数组（可能来自缓存）
        byte[] fileBytes = getDocumentBytes(minioUrl);
        
        // 每次都创建新的Document对象，避免状态问题
        ByteArrayInputStream inputStream = new ByteArrayInputStream(fileBytes);
        Document doc = new Document(inputStream);
        
        log.debug("创建Document对象，页数: {}", doc.getPageCount());
        
        return doc;
    }
    
    /**
     * 从MinIO URL提取objectName
     * 
     * 支持两种URL格式：
     * 1. http://localhost:9000/bucket-name/reports/xxx.docx
     * 2. https://domain.com/minio/bucket-name/reports/xxx.docx
     * 
     * @param minioUrl 完整MinIO URL
     * @return objectName（如：reports/xxx.docx，不含bucket名称）
     */
    private String extractObjectName(String minioUrl) {
        try {
            java.net.URL url = new java.net.URL(minioUrl);
            String path = url.getPath();
            
            log.debug("原始URL路径: {}", path);
            
            // 查找bucket名称的位置（从配置获取）
            String bucketName = minioFileService.getBucketName();
            int bucketIndex = path.indexOf("/" + bucketName + "/");
            
            if (bucketIndex != -1) {
                // 找到bucket名称，返回bucket后面的内容
                String objectName = path.substring(bucketIndex + bucketName.length() + 2);
                log.debug("提取的objectName: {}", objectName);
                return objectName;
            }
            
            // 降级方案：移除开头的"/"和第一个路径段（假设是bucket）
            String[] parts = path.split("/");
            if (parts.length >= 3) {
                // parts[0]="", parts[1]=可能的前缀或bucket, parts[2]=...
                // 尝试找到bucket名称
                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].equals(bucketName)) {
                        // 找到bucket，返回后面的部分
                        return String.join("/", java.util.Arrays.copyOfRange(parts, i + 1, parts.length));
                    }
                }
                
                // 如果找不到bucket名称，假设第一个非空段后面是objectName
                return String.join("/", java.util.Arrays.copyOfRange(parts, 2, parts.length));
            }
            
            // 最后降级：直接使用path（移除开头的/）
            log.warn("无法正确解析MinIO URL，使用降级方案: {}", path);
            return path.substring(1);
        } catch (Exception e) {
            log.error("解析MinIO URL失败: {}", minioUrl, e);
            throw new RuntimeException("无效的MinIO URL: " + minioUrl);
        }
    }
    
    /**
     * 获取文档基本信息
     * 
     * @param minioUrl MinIO文件URL
     * @return 文档信息
     */
    public DocumentInfo getDocumentInfo(String minioUrl) throws Exception {
        log.info("获取文档信息: {}", minioUrl);
        
        // 获取文档字节数组（有缓存）
        byte[] docBytes = getDocumentBytes(minioUrl);
        
        // 创建Document对象
        Document doc = new Document(new ByteArrayInputStream(docBytes));
        
        String objectName = extractObjectName(minioUrl);
        
        // 获取文件信息
        java.util.Map<String, Object> fileInfo = minioFileService.getFileInfo(objectName);
        Long fileSize = (Long) fileInfo.get("size");
        
        // 检测是否有批注（复用已下载的字节数组）
        boolean hasComments = false;
        try {
            hasComments = checkDocHasComments(docBytes);
        } catch (Exception e) {
            log.debug("检测批注失败: {}", e.getMessage());
        }
        
        // 提取文件名
        String fileName = objectName;
        if (objectName.contains("/")) {
            fileName = objectName.substring(objectName.lastIndexOf("/") + 1);
        }
        
        // 确定格式
        String format = "docx";
        if (fileName.toLowerCase().endsWith(".doc")) {
            format = "doc";
        }
        
        return DocumentInfo.builder()
                .fileName(fileName)
                .fileSize(fileSize != null ? fileSize : 0)
                .pageCount(doc.getPageCount())
                .format(format)
                .hasComments(hasComments)
                .title(doc.getBuiltInDocumentProperties().getTitle())
                .minioUrl(minioUrl)
                .build();
    }
    
    /**
     * 获取单页缩略图
     * 
     * @param minioUrl MinIO文件URL
     * @param pageNumber 页码（从1开始）
     * @return 缩略图信息
     */
    public ThumbnailInfo getPageThumbnail(String minioUrl, int pageNumber) throws Exception {
        log.info("生成第{}页缩略图: {}", pageNumber, minioUrl);
        
        String cacheKey = minioUrl + "_thumb_" + pageNumber;
        
        // 检查缩略图缓存
        if (thumbnailCache.containsKey(cacheKey)) {
            log.debug("从缓存获取缩略图: 第{}页", pageNumber);
            return ThumbnailInfo.builder()
                    .pageNumber(pageNumber)
                    .thumbnailBase64(thumbnailCache.get(cacheKey))
                    .build();
        }
        
        Document doc = loadDocumentFromMinio(minioUrl);
        
        // 验证页码
        if (pageNumber < 1 || pageNumber > doc.getPageCount()) {
            throw new IllegalArgumentException("页码超出范围: " + pageNumber);
        }
        
        // 配置图像保存选项（参考ai-backend优化）
        ImageSaveOptions options = new ImageSaveOptions(SaveFormat.PNG);
        options.setPageSet(new PageSet(pageNumber - 1)); // Aspose页码从0开始
        options.setResolution(96); // DPI分辨率
        options.setScale(0.5f); // 缩略图缩放比例（0.5 = 50%大小，减少传输）
        options.setUseAntiAliasing(true); // 抗锯齿
        options.setUseHighQualityRendering(true); // 高质量渲染
        
        // 生成PNG图片
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        doc.save(outputStream, options);
        
        byte[] imageBytes = outputStream.toByteArray();
        
        // 获取图片尺寸
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        int width = image.getWidth();
        int height = image.getHeight();
        
        // Base64编码
        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        
        // 缓存
        thumbnailCache.put(cacheKey, base64);
        
        log.info("缩略图生成成功: 第{}页, 尺寸: {}x{}, 大小: {} KB", 
                pageNumber, width, height, imageBytes.length / 1024);
        
        return ThumbnailInfo.builder()
                .pageNumber(pageNumber)
                .thumbnailBase64(base64)
                .width(width)
                .height(height)
                .size(imageBytes.length)
                .build();
    }
    
    /**
     * 获取所有页的缩略图
     * 
     * @param minioUrl MinIO文件URL
     * @return 缩略图列表
     */
    public List<ThumbnailInfo> getAllThumbnails(String minioUrl) throws Exception {
        log.info("生成所有页缩略图: {}", minioUrl);
        
        Document doc = loadDocumentFromMinio(minioUrl);
        int pageCount = doc.getPageCount();
        
        List<ThumbnailInfo> thumbnails = new ArrayList<>();
        
        for (int i = 1; i <= pageCount; i++) {
            try {
                ThumbnailInfo thumbnail = getPageThumbnail(minioUrl, i);
                thumbnails.add(thumbnail);
            } catch (Exception e) {
                log.error("生成第{}页缩略图失败", i, e);
                // 继续处理其他页
            }
        }
        
        log.info("所有缩略图生成完成: 共{}页", thumbnails.size());
        
        return thumbnails;
    }
    
    /**
     * 获取单页SVG
     * 
     * @param minioUrl MinIO文件URL
     * @param pageNumber 页码（从1开始）
     * @return SVG文本
     */
    public String getPageSvg(String minioUrl, int pageNumber) throws Exception {
        log.info("导出第{}页为SVG: {}", pageNumber, minioUrl);
        
        Document doc = loadDocumentFromMinio(minioUrl);
        
        // 验证页码
        if (pageNumber < 1 || pageNumber > doc.getPageCount()) {
            throw new IllegalArgumentException("页码超出范围: " + pageNumber);
        }
        
        // 配置SVG保存选项（参考ai-backend优化）
        SvgSaveOptions options = new SvgSaveOptions();
        options.setPageSet(new PageSet(pageNumber - 1));
        options.setFitToViewPort(true); // 适应视口
        options.setExportEmbeddedImages(true); // 嵌入图片
        options.setUseHighQualityRendering(true); // 高质量渲染
        options.setShowPageBorder(false); // 不显示页面边框
        options.setTextOutputMode(SvgTextOutputMode.USE_TARGET_MACHINE_FONTS); // 使用目标机器字体
        options.setResourcesFolder(null); // 嵌入资源，不外部引用
        
        // 导出为SVG
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        doc.save(outputStream, options);
        
        String svg = outputStream.toString("UTF-8");
        
        // 【核心】清理SVG中的重复ID，修复Aspose生成的问题
        svg = cleanDuplicateIds(svg);
        
        log.info("SVG导出成功: 第{}页, 大小: {} KB", pageNumber, svg.length() / 1024);
        
        return svg;
    }
    
    /**
     * 批量获取页面SVG
     * 
     * @param minioUrl MinIO文件URL
     * @param startPage 起始页（从1开始）
     * @param endPage 结束页
     * @return SVG列表
     */
    public List<String> getPagesSvg(String minioUrl, int startPage, int endPage) throws Exception {
        log.info("批量导出SVG: 第{}-{}页, {}", startPage, endPage, minioUrl);
        
        Document doc = loadDocumentFromMinio(minioUrl);
        int pageCount = doc.getPageCount();
        
        // 验证页码范围
        if (startPage < 1) startPage = 1;
        if (endPage > pageCount) endPage = pageCount;
        if (startPage > endPage) {
            throw new IllegalArgumentException("起始页不能大于结束页");
        }
        
        List<String> svgList = new ArrayList<>();
        
        for (int i = startPage; i <= endPage; i++) {
            try {
                String svg = getPageSvg(minioUrl, i);
                svgList.add(svg);
            } catch (Exception e) {
                log.error("导出第{}页SVG失败", i, e);
                svgList.add(""); // 添加空字符串占位
            }
        }
        
        log.info("批量SVG导出完成: {}页", svgList.size());
        
        return svgList;
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        documentBytesCache.clear();
        thumbnailCache.clear();
        log.info("文档预览缓存已清理");
    }
    
    /**
     * 清理指定文档的缓存
     * 
     * @param minioUrl MinIO文件URL
     */
    public void clearDocumentCache(String minioUrl) {
        documentBytesCache.remove(minioUrl);
        
        // 清理相关的缩略图缓存
        thumbnailCache.keySet().removeIf(key -> key.startsWith(minioUrl));
        
        log.info("已清理文档缓存: {}", minioUrl);
    }
    
    /**
     * 检查DOCX文档是否包含批注
     * 
     * @param docBytes 文档字节数组
     * @return 是否包含批注
     */
    private boolean checkDocHasComments(byte[] docBytes) {
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
     * 清理SVG中的重复ID属性和重复的id属性定义
     * 
     * 修复Aspose生成的SVG中可能出现的以下问题：
     * 1. 移除同一元素上重复的id属性，保留第一个（XML要求单个id属性）
     * 2. 处理重复的ID值，修改为唯一的，格式为: originalId_2, originalId_3 等
     * 3. 移除文本内容中显示的id属性值
     * 
     * 这解决了"Attribute id redefined"错误和浏览器渲染问题
     *
     * @param svg SVG字符串内容
     * @return 清理后的SVG字符串
     */
    private String cleanDuplicateIds(String svg) {
        // 第一步：处理同一元素上的多个id属性 (XML属性重复定义问题)
        // 使用循环来处理多个id的情况，每次移除一个额外的id属性
        int maxIterations = 10;
        int iteration = 0;
        while (svg.contains("id=\"") && iteration < maxIterations) {
            // 匹配形如: id="xxx" ... id="yyy" 的模式，其中中间没有 >
            String before = svg;
            svg = svg.replaceAll("(\\sid=\"[^\"]*\")\\s+(id=\"[^\"]*\"(?=[^<>]*(?:<|$)))", "$1");
            svg = svg.replaceAll("(\\sid=\"[^\"]*\")\\s+(id=\"[^\"]*\")", "$1");

            // 如果没有改变，说明没有更多的重复id属性，退出循环
            if (svg.equals(before)) {
                break;
            }
            iteration++;
        }

        // 第二步：使用Map记录每个ID值出现的次数，处理重复的ID值
        Map<String, Integer> idCountMap = new HashMap<>();

        // 正则表达式匹配所有的id属性（现在应该每个元素只有一个）
        Pattern idPattern = Pattern.compile("id=\"([^\"]*)\"");
        Matcher idMatcher = idPattern.matcher(svg);

        StringBuffer result = new StringBuffer();
        while (idMatcher.find()) {
            String originalId = idMatcher.group(1);

            // 记录ID的出现次数
            int count = idCountMap.getOrDefault(originalId, 0) + 1;
            idCountMap.put(originalId, count);

            // 如果是重复ID值（count > 1），添加后缀使其唯一
            String newId = originalId;
            if (count > 1) {
                newId = originalId + "_" + count;
            }

            // 替换当前的id属性
            idMatcher.appendReplacement(result, "id=\"" + Matcher.quoteReplacement(newId) + "\"");
        }
        idMatcher.appendTail(result);
        svg = result.toString();

        // 第三步：移除在文本内容中出现的id属性字符串
        svg = removeIdStringsFromText(svg);

        log.debug("SVG清理完成：处理了{}个唯一ID", idCountMap.size());

        return svg;
    }

    /**
     * 从SVG文本内容中移除id属性字符串
     * 
     * 处理了Aspose将某些id值当作文本内容输出的问题
     * 策略：逐个处理元素，确保只在元素属性中保留id，不在文本内容中显示
     *
     * @param svg SVG字符串
     * @return 清理后的SVG字符串
     */
    private String removeIdStringsFromText(String svg) {
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        // 找出所有的标签
        Pattern tagPattern = Pattern.compile("<[^>]+>");
        Matcher tagMatcher = tagPattern.matcher(svg);

        while (tagMatcher.find()) {
            int tagStart = tagMatcher.start();
            int tagEnd = tagMatcher.end();

            // 添加标签之前的内容（移除其中的id="..." 字符串）
            String textContent = svg.substring(lastEnd, tagStart);
            textContent = textContent.replaceAll("id=\"[^\"]*\"\\s*", "");
            result.append(textContent);

            // 添加标签本身
            result.append(tagMatcher.group());

            lastEnd = tagEnd;
        }

        // 添加最后的内容
        if (lastEnd < svg.length()) {
            String textContent = svg.substring(lastEnd);
            textContent = textContent.replaceAll("id=\"[^\"]*\"\\s*", "");
            result.append(textContent);
        }

        return result.toString();
    }
}

