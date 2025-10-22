package com.example.Contract_review.service;

import com.example.Contract_review.model.ParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parse 结果缓存服务
 *
 * 【工作流程修复】用于存储 Parse 阶段生成的带锚点文档和解析结果
 *
 * 问题背景：
 * - /generate-prompt 端点生成带锚点文档
 * - /import-result-xml 端点需要使用同一个文档进行批注
 * - 但用户可能上传不同的文件，导致锚点丢失
 *
 * 解决方案：
 * - Parse 阶段生成 parseResultId 并缓存文档
 * - Annotate 阶段使用 parseResultId 检索缓存的文档
 * - 保证 Parse 和 Annotate 使用完全相同的文档
 *
 * @author Claude Code
 * @version 2.3.0
 */
@Component
public class ParseResultCache {

    private static final Logger logger = LoggerFactory.getLogger(ParseResultCache.class);

    /**
     * 缓存的 Parse 结果
     */
    public static class CachedParseResult {
        /**
         * 解析结果
         */
        public final ParseResult parseResult;

        /**
         * 带锚点的文档字节数据
         */
        public final byte[] documentWithAnchorsBytes;

        /**
         * 缓存创建时间戳
         */
        public final long timestamp;

        /**
         * 缓存的源文件名
         */
        public final String sourceFilename;

        public CachedParseResult(ParseResult parseResult, byte[] documentBytes, String sourceFilename) {
            this.parseResult = parseResult;
            this.documentWithAnchorsBytes = documentBytes;
            this.sourceFilename = sourceFilename;
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * 检查缓存是否已过期
         * @param ttlMinutes 缓存生存时间（分钟）
         * @return true 表示已过期
         */
        public boolean isExpired(long ttlMinutes) {
            long elapsedMs = System.currentTimeMillis() - timestamp;
            long ttlMs = ttlMinutes * 60 * 1000L;
            return elapsedMs > ttlMs;
        }

        /**
         * 获取缓存年龄（秒）
         */
        public long getAgeSeconds() {
            return (System.currentTimeMillis() - timestamp) / 1000;
        }
    }

    /**
     * 缓存存储：parseResultId → CachedParseResult
     */
    private static final Map<String, CachedParseResult> cache = new ConcurrentHashMap<>();

    /**
     * 缓存生存时间（分钟）
     * 设置为 240 分钟（4小时），足够用户完成 Parse → ChatGPT 审查 → Annotate 的整个流程
     *
     * 修复说明：
     * - 原设置：30 分钟太短，用户审查时间通常 30-60 分钟，导致缓存过期
     * - 新设置：240 分钟（4小时），覆盖典型工作流周期
     * - 内存影响：4小时 * 10个缓存 * 50KB ≈ 2MB，可接受
     */
    private static final long DEFAULT_TTL_MINUTES = 240;

    /**
     * 存储 Parse 结果到缓存
     *
     * @param parseResult 解析结果
     * @param documentBytes 带锚点的文档字节数据
     * @param sourceFilename 源文件名
     * @return parseResultId - 用于后续检索的唯一 ID
     */
    public String store(ParseResult parseResult, byte[] documentBytes, String sourceFilename) {
        String cacheId = UUID.randomUUID().toString();

        CachedParseResult cached = new CachedParseResult(parseResult, documentBytes, sourceFilename);
        cache.put(cacheId, cached);

        logger.info("【缓存】Parse 结果已存储: parseResultId={}, 条款数={}, 文档大小={} 字节, 文件名={}",
                   cacheId, parseResult.getClauses().size(), documentBytes.length, sourceFilename);

        return cacheId;
    }

    /**
     * 从缓存检索 Parse 结果
     *
     * @param cacheId parseResultId
     * @return CachedParseResult 如果存在且未过期，否则返回 null
     */
    public CachedParseResult retrieve(String cacheId) {
        if (cacheId == null || cacheId.isEmpty()) {
            return null;
        }

        CachedParseResult result = cache.get(cacheId);

        if (result == null) {
            logger.warn("【缓存】Parse 结果不存在: parseResultId={}", cacheId);
            return null;
        }

        // 检查是否过期（30 分钟）
        if (result.isExpired(DEFAULT_TTL_MINUTES)) {
            logger.warn("【缓存】Parse 结果已过期（{}分钟）: parseResultId={}, 文件名={}",
                       DEFAULT_TTL_MINUTES, cacheId, result.sourceFilename);
            cache.remove(cacheId);
            return null;
        }

        logger.info("【缓存】Parse 结果已检索: parseResultId={}, 年龄={} 秒, 条款数={}, 文件名={}",
                   cacheId, result.getAgeSeconds(), result.parseResult.getClauses().size(), result.sourceFilename);

        return result;
    }

    /**
     * 主动移除缓存
     *
     * @param cacheId parseResultId
     */
    public void evict(String cacheId) {
        if (cache.remove(cacheId) != null) {
            logger.info("【缓存】Parse 结果已移除: parseResultId={}", cacheId);
        }
    }

    /**
     * 清理所有已过期的缓存
     *
     * @return 清理的缓存数量
     */
    public int cleanupExpired() {
        int removed = 0;

        for (String key : cache.keySet()) {
            CachedParseResult result = cache.get(key);
            if (result != null && result.isExpired(DEFAULT_TTL_MINUTES)) {
                if (cache.remove(key) != null) {
                    removed++;
                    logger.debug("【缓存】清理过期项: parseResultId={}", key);
                }
            }
        }

        if (removed > 0) {
            logger.info("【缓存】已清理 {} 个过期项, 当前缓存大小: {}", removed, cache.size());
        }

        return removed;
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalCached", cache.size());
        stats.put("ttlMinutes", DEFAULT_TTL_MINUTES);

        int expiredCount = 0;
        long totalSize = 0;
        int totalClauses = 0;

        for (CachedParseResult result : cache.values()) {
            if (result.isExpired(DEFAULT_TTL_MINUTES)) {
                expiredCount++;
            }
            totalSize += result.documentWithAnchorsBytes.length;
            totalClauses += result.parseResult.getClauses().size();
        }

        stats.put("expiredCount", expiredCount);
        stats.put("totalDocumentSize", totalSize);
        stats.put("totalClauses", totalClauses);

        return stats;
    }
}
