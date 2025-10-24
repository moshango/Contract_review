package com.example.Contract_review.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合同方名称提取工具
 *
 * 从合同文本中识别甲方和乙方的名称
 * 支持多种常见表述形式
 */
public class PartyNameExtractor {

    private static final Logger logger = LoggerFactory.getLogger(PartyNameExtractor.class);

    /**
     * 规定方名称识别模式集合
     */
    private static final Map<String, Pattern> PARTY_PATTERNS = new HashMap<>();

    static {
        // 模式1: 形如 "甲方：xxx公司" 或 "甲方: xxx公司"
        PARTY_PATTERNS.put("standard_colon", Pattern.compile("[（(]?甲方[）)，、：:]*([^，\\n（()））；;。]*?)(?:[\\s，\\n（()））；;。]|$)"));

        // 模式2: 形如 "甲方为xxx公司"
        PARTY_PATTERNS.put("be_pattern", Pattern.compile("甲方\\s*(?:为|是|系)\\s*([^，\\n（()））；;。]*?)(?:[，\\n（()））；;。]|$)"));

        // 模式3: 形如 "甲方（乙方）：xxx（yyy）"
        PARTY_PATTERNS.put("parenthesis", Pattern.compile("甲方.*?[：:]\\s*([^，\\n（()）)；;。]*?)(?:[，\\n；;。]|$)"));

        // 模式4: 标题/首字段中的甲方乙方
        PARTY_PATTERNS.put("heading", Pattern.compile("([^甲乙\\s]*(?:公司|集团|企业|有限|股份|法人|机构|部门)[^,。\\n]*?)\\s*和\\s*([^甲乙\\s]*(?:公司|集团|企业|有限|股份|法人|机构|部门)[^,。\\n]*)"));
    }

    /**
     * 从合同文本中提取甲方和乙方名称
     *
     * @param contractText 合同全文（或前500个字）
     * @return 包含 partyA 和 partyB 的 Map，如果未找到则为 null
     */
    public static Map<String, String> extractPartyNames(String contractText) {
        if (contractText == null || contractText.isEmpty()) {
            return null;
        }

        // 为了提高效率，仅在前 2000 个字符中查找
        String searchText = contractText.length() > 2000 ?
                           contractText.substring(0, 2000) :
                           contractText;

        Map<String, String> result = new HashMap<>();
        String partyA = null;
        String partyB = null;

        // 按优先级尝试不同模式
        try {
            // 首先尝试提取甲方名称
            partyA = extractPartyA(searchText);

            // 然后尝试提取乙方名称
            partyB = extractPartyB(searchText);

            // 清理和验证提取的名称
            if (partyA != null) {
                partyA = cleanPartyName(partyA);
                if (partyA.isEmpty()) {
                    partyA = null;
                }
            }

            if (partyB != null) {
                partyB = cleanPartyName(partyB);
                if (partyB.isEmpty()) {
                    partyB = null;
                }
            }

            // 只有两个都找到才返回结果
            if (partyA != null && partyB != null) {
                result.put("partyA", partyA);
                result.put("partyB", partyB);
                logger.info("✓ 成功识别甲乙方：甲方={}, 乙方={}", partyA, partyB);
                return result;
            }

            logger.warn("⚠ 未能完全识别甲乙方（只找到甲方: {}, 乙方: {}），使用名称为 null", partyA, partyB);
            return null;

        } catch (Exception e) {
            logger.error("❌ 提取甲乙方名称时出错", e);
            return null;
        }
    }

    /**
     * 提取甲方名称
     */
    private static String extractPartyA(String text) {
        // 模式1: 标准的 "甲方：xxx" 形式（最常见）
        Pattern pattern = Pattern.compile("甲\\s*方[\\s：:，、]*([^\\n，。；;（()）)、]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty() && name.length() < 100 && !name.matches(".*[为是系].*")) {
                return name;
            }
        }

        // 模式2: "甲方为" 或 "甲方是" 或其他动词形式
        pattern = Pattern.compile("甲\\s*方\\s*(?:为|是|系)\\s*([^\\n，。；;（()）)、]+)");
        matcher = pattern.matcher(text);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty() && name.length() < 100) {
                return name;
            }
        }

        // 模式3: "甲方（xxx）" 或 "甲方(xxx)"
        pattern = Pattern.compile("甲\\s*方\\s*[（(]\\s*([^）)]+)\\s*[）)]");
        matcher = pattern.matcher(text);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty() && name.length() < 100) {
                return name;
            }
        }

        // 模式4: "甲方 xxx" (仅空格分隔，后面跟着句号或其他标点)
        pattern = Pattern.compile("甲\\s*方\\s+([^\\n，。；;（()）)、：:]+?)(?=[，。；;（()）)\\n\\s]|$)");
        matcher = pattern.matcher(text);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty() && name.length() < 100 && name.length() > 1) {
                return name;
            }
        }

        return null;
    }

    /**
     * 提取乙方名称
     */
    private static String extractPartyB(String text) {
        // 模式1: 标准的 "乙方：xxx" 形式（最常见）
        Pattern pattern = Pattern.compile("乙\\s*方[\\s：:，、]*([^\\n，。；;（()）)、]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty() && name.length() < 100 && !name.matches(".*[为是系].*")) {
                return name;
            }
        }

        // 模式2: "乙方为" 或 "乙方是" 或其他动词形式
        pattern = Pattern.compile("乙\\s*方\\s*(?:为|是|系)\\s*([^\\n，。；;（()）)、]+)");
        matcher = pattern.matcher(text);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty() && name.length() < 100) {
                return name;
            }
        }

        // 模式3: "乙方（xxx）" 或 "乙方(xxx)"
        pattern = Pattern.compile("乙\\s*方\\s*[（(]\\s*([^）)]+)\\s*[）)]");
        matcher = pattern.matcher(text);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty() && name.length() < 100) {
                return name;
            }
        }

        // 模式4: "乙方 xxx" (仅空格分隔，后面跟着句号或其他标点)
        pattern = Pattern.compile("乙\\s*方\\s+([^\\n，。；;（()）)、：:]+?)(?=[，。；;（()）)\\n\\s]|$)");
        matcher = pattern.matcher(text);
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty() && name.length() < 100 && name.length() > 1) {
                return name;
            }
        }

        return null;
    }

    /**
     * 清理提取的名称
     * - 移除多余的空白字符
     * - 移除括号和特殊符号前缀
     * - 只保留有效的内容（公司名称）
     */
    private static String cleanPartyName(String name) {
        if (name == null) {
            return null;
        }

        // 移除所有空白字符（包括换行）
        name = name.replaceAll("\\s+", "");

        // 移除前后括号
        name = name.replaceAll("^[（(]+", "").replaceAll("[）)]+$", "");

        // 移除数字前缀（如 "一、")
        name = name.replaceAll("^[0-9一二三四五六七八九]+[、\\.\\.)\\)）]*", "");

        // 如果包含多个信息块，只保留第一个（由多个分隔符分隔）
        if (name.contains("；") || name.contains(";")) {
            name = name.split("[；;]")[0];
        }
        if (name.contains("，")) {
            name = name.split("，")[0];
        }
        if (name.contains(",")) {
            name = name.split(",")[0];
        }

        // 修剪
        name = name.trim();

        // 验证长度（公司名不会超过 50 个字）
        if (name.length() > 50 || name.length() == 0) {
            return null;
        }

        return name;
    }
}
