package com.example.Contract_review.service;

import com.example.Contract_review.model.ReviewStance;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 审查立场服务
 *
 * 管理用户的审查立场设置
 * 支持会话级别和全局级别的立场配置
 */
@Service
public class ReviewStanceService {

    /**
     * 线程本地存储，用于存储当前线程（会话）的立场
     */
    private static final ThreadLocal<ReviewStance> threadLocalStance = new ThreadLocal<>();

    /**
     * 全局立场配置（支持多用户场景下的不同立场）
     * Key: sessionId 或 userId
     * Value: ReviewStance
     */
    private final ConcurrentHashMap<String, ReviewStance> stanceCache = new ConcurrentHashMap<>();

    /**
     * 默认立场（中立）
     */
    private static final ReviewStance DEFAULT_STANCE = ReviewStance.neutral();

    /**
     * 设置当前会话的立场
     *
     * @param stance 审查立场
     */
    public void setStance(ReviewStance stance) {
        if (stance == null) {
            threadLocalStance.set(DEFAULT_STANCE);
        } else {
            threadLocalStance.set(stance);
        }
    }

    /**
     * 设置当前会话的立场（按党派标识）
     *
     * @param partyId "A" (甲方) / "B" (乙方) / 其他（中立）
     */
    public void setStanceByParty(String partyId) {
        ReviewStance stance = ReviewStance.fromPartyId(partyId);
        setStance(stance);
    }

    /**
     * 获取当前会话的立场
     *
     * @return 当前立场，如果未设置则返回默认的中立立场
     */
    public ReviewStance getStance() {
        ReviewStance stance = threadLocalStance.get();
        return stance != null ? stance : DEFAULT_STANCE;
    }

    /**
     * 获取当前会话的党派标识
     *
     * @return "A" (甲方) / "B" (乙方) / null (中立)
     */
    public String getParty() {
        ReviewStance stance = getStance();
        return stance.getParty();
    }

    /**
     * 清除当前会话的立场设置
     */
    public void clearStance() {
        threadLocalStance.remove();
    }

    /**
     * 设置特定会话的立场（适用于多用户场景）
     *
     * @param sessionId 会话ID
     * @param stance 审查立场
     */
    public void setStanceForSession(String sessionId, ReviewStance stance) {
        if (stance == null) {
            stanceCache.remove(sessionId);
        } else {
            stanceCache.put(sessionId, stance);
        }
    }

    /**
     * 获取特定会话的立场（适用于多用户场景）
     *
     * @param sessionId 会话ID
     * @return 对应的立场，如果未设置则返回默认的中立立场
     */
    public ReviewStance getStanceForSession(String sessionId) {
        return stanceCache.getOrDefault(sessionId, DEFAULT_STANCE);
    }

    /**
     * 清除特定会话的立场设置
     *
     * @param sessionId 会话ID
     */
    public void clearStanceForSession(String sessionId) {
        stanceCache.remove(sessionId);
    }

    /**
     * 检查是否已设置了非默认立场
     *
     * @return true 如果当前立场不是中立立场
     */
    public boolean isStanceSet() {
        ReviewStance stance = getStance();
        return stance != null && stance.getParty() != null && !stance.getParty().isEmpty();
    }

    /**
     * 获取立场描述（用于日志和前端显示）
     *
     * @return 立场描述文本
     */
    public String getStanceDescription() {
        return getStance().getDescription();
    }
}
