package com.example.Contract_review.service;

import com.example.Contract_review.model.PartyExtractionRequest;
import com.example.Contract_review.model.PartyExtractionResponse;
import com.example.Contract_review.qwen.client.QwenClient;
import com.example.Contract_review.qwen.dto.ChatMessage;
import com.example.Contract_review.qwen.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 合同方信息提取服务
 *
 * 用 Qwen 替代文本搜索来识别合同中的甲乙方信息
 * 返回识别结果和建议的审查立场
 */
@Slf4j
@Service
public class PartyExtractionService {

    @Autowired
    private QwenClient qwenClient;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String QWEN_MODEL = "qwen-max-latest";
    private static final long TIMEOUT = 30000; // 30 seconds

    /**
     * 使用 Qwen 提取合同方信息
     *
     * 改进的多层提取策略：
     * 1. 先使用本地规则进行初步提取（快速、准确）
     * 2. 如果本地提取失败，调用 Qwen 进行二次提取
     * 3. 合并本地和 Qwen 的结果
     *
     * @param request 包含合同文本的请求
     * @return 包含识别的甲乙方和推荐立场的响应
     */
    public PartyExtractionResponse extractPartyInfoWithQwen(PartyExtractionRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("=== 开始使用改进的多层提取策略 ===");

            if (request.getContractText() == null || request.getContractText().isEmpty()) {
                log.error("合同文本为空");
                return PartyExtractionResponse.builder()
                    .success(false)
                    .errorMessage("合同文本为空")
                    .processingTime(System.currentTimeMillis() - startTime)
                    .build();
            }

            // 第一步：使用本地规则进行快速提取
            log.info("第一步：使用本地规则进行快速提取");
            PartyExtractionResponse localResult = extractPartiesLocally(request.getContractText());

            if (localResult.isSuccess() && localResult.getPartyA() != null && localResult.getPartyB() != null) {
                log.info("✓ 本地提取成功: A={}, B={}", localResult.getPartyA(), localResult.getPartyB());
                localResult.setProcessingTime(System.currentTimeMillis() - startTime);
                return localResult;
            }

            // 第二步：如果本地提取失败，调用 Qwen 进行二次提取
            log.info("第二步：本地提取失败，调用 Qwen 进行补充提取");

            if (!isQwenAvailable()) {
                log.warn("Qwen 服务不可用，返回本地提取结果");
                localResult.setProcessingTime(System.currentTimeMillis() - startTime);
                return localResult;
            }

            // 构建 Qwen 消息
            ChatMessage systemMsg = ChatMessage.builder()
                .role("system")
                .content("你是一个专业的合同分析助手。你的任务是准确识别合同中的甲乙方名称。\n" +
                        "【识别规则】\n" +
                        "1. 只识别和返回甲乙两方，忽略其他方\n" +
                        "2. 支持各种标签映射：\n" +
                        "   - A 方：甲方、买方、需方、发包人、客户、订购方、用户、委托方\n" +
                        "   - B 方：乙方、卖方、供方、承包人、服务商、承接方、受托方\n" +
                        "3. 必须返回完整的公司名称（不要截断）\n" +
                        "4. 返回 JSON 格式，包含：partyA、partyB、partyARoleName、partyBRoleName、" +
                        "recommendedStance、stanceReason\n" +
                        "【重要】必须返回有效的 partyA 和 partyB（不能为 null）")
                .build();

            ChatMessage userMsg = ChatMessage.builder()
                .role("user")
                .content(generateQwenExtractionPrompt(request, localResult))
                .build();

            List<ChatMessage> messages = Arrays.asList(systemMsg, userMsg);

            log.info("向Qwen发送二次提取请求");

            // 调用 Qwen API
            ChatResponse response = qwenClient.chat(messages, QWEN_MODEL).block();

            if (response == null) {
                log.error("Qwen返回null响应，返回本地提取结果");
                localResult.setProcessingTime(System.currentTimeMillis() - startTime);
                return localResult;
            }

            String responseContent = response.extractContent();
            log.debug("Qwen返回内容长度: {} 字符", responseContent.length());

            // 提取和解析JSON
            String jsonResult = extractJsonFromResponse(responseContent);

            if (jsonResult == null || jsonResult.isEmpty()) {
                log.warn("无法从Qwen返回中提取JSON，返回本地提取结果");
                localResult.setProcessingTime(System.currentTimeMillis() - startTime);
                return localResult;
            }

            // 解析JSON并构建响应
            try {
                ObjectNode json = (ObjectNode) objectMapper.readTree(jsonResult);

                PartyExtractionResponse qwenResult = PartyExtractionResponse.builder()
                    .success(true)
                    .partyA(json.has("partyA") && !json.get("partyA").isNull() ? json.get("partyA").asText() : null)
                    .partyB(json.has("partyB") && !json.get("partyB").isNull() ? json.get("partyB").asText() : null)
                    .partyARoleName(json.has("partyARoleName") && !json.get("partyARoleName").isNull() ? json.get("partyARoleName").asText() : null)
                    .partyBRoleName(json.has("partyBRoleName") && !json.get("partyBRoleName").isNull() ? json.get("partyBRoleName").asText() : null)
                    .recommendedStance(json.has("recommendedStance") && !json.get("recommendedStance").isNull() ? json.get("recommendedStance").asText() : null)
                    .stanceReason(json.has("stanceReason") && !json.get("stanceReason").isNull() ? json.get("stanceReason").asText() : null)
                    .processingTime(System.currentTimeMillis() - startTime)
                    .build();

                // 第三步：合并结果（优先使用本地提取，补充 Qwen 结果）
                if (qwenResult.getPartyA() != null) {
                    localResult.setPartyA(qwenResult.getPartyA());
                }
                if (qwenResult.getPartyB() != null) {
                    localResult.setPartyB(qwenResult.getPartyB());
                }
                if (qwenResult.getPartyARoleName() != null) {
                    localResult.setPartyARoleName(qwenResult.getPartyARoleName());
                }
                if (qwenResult.getPartyBRoleName() != null) {
                    localResult.setPartyBRoleName(qwenResult.getPartyBRoleName());
                }
                if (qwenResult.getRecommendedStance() != null) {
                    localResult.setRecommendedStance(qwenResult.getRecommendedStance());
                }
                if (qwenResult.getStanceReason() != null) {
                    localResult.setStanceReason(qwenResult.getStanceReason());
                }

                log.info("✓ Qwen 提取完成，合并后结果: A={}, B={}",
                    localResult.getPartyA(), localResult.getPartyB());

                localResult.setSuccess(true);
                localResult.setProcessingTime(System.currentTimeMillis() - startTime);
                return localResult;

            } catch (Exception e) {
                log.error("Qwen JSON解析失败: {}", e.getMessage());
                localResult.setProcessingTime(System.currentTimeMillis() - startTime);
                return localResult;
            }

        } catch (Exception e) {
            log.error("合同方提取失败", e);
            return PartyExtractionResponse.builder()
                .success(false)
                .errorMessage("提取失败: " + e.getMessage())
                .processingTime(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    /**
     * 第一阶段：提取关键词位置和上下文
     * 不进行直接提取，而是找到关键词位置，提取其周围上下文给 Qwen 处理
     */
    private PartyExtractionResponse extractPartiesLocally(String contractText) {
        log.info("第一阶段：提取关键词位置和上下文...");

        // 取前 3000 个字进行分析
        String searchText = contractText.length() > 3000 ?
                           contractText.substring(0, 3000) : contractText;

        // 定义所有支持的标签关键词
        String[] partyAKeywords = {"甲方", "买方", "需方", "发包人", "客户", "订购方", "用户", "委托方"};
        String[] partyBKeywords = {"乙方", "卖方", "供方", "承包人", "服务商", "承接方", "受托方"};

        // 查找甲方关键词
        String partyAContext = null;
        String partyARole = null;
        for (String keyword : partyAKeywords) {
            int index = searchText.indexOf(keyword);
            if (index != -1) {
                // 提取关键词周围的上下文（前50字，后50字）
                int start = Math.max(0, index - 50);
                int end = Math.min(searchText.length(), index + keyword.length() + 50);
                partyAContext = searchText.substring(start, end);
                partyARole = keyword;
                log.info("✓ 找到甲方关键词: {}, 上下文长度: {} 字", keyword, partyAContext.length());
                break;
            }
        }

        // 查找乙方关键词
        String partyBContext = null;
        String partyBRole = null;
        for (String keyword : partyBKeywords) {
            int index = searchText.indexOf(keyword);
            if (index != -1) {
                // 提取关键词周围的上下文（前50字，后50字）
                int start = Math.max(0, index - 50);
                int end = Math.min(searchText.length(), index + keyword.length() + 50);
                partyBContext = searchText.substring(start, end);
                partyBRole = keyword;
                log.info("✓ 找到乙方关键词: {}, 上下文长度: {} 字", keyword, partyBContext.length());
                break;
            }
        }

        // 如果找到了关键词，返回上下文供 Qwen 处理
        if (partyAContext != null && partyBContext != null) {
            return PartyExtractionResponse.builder()
                .success(false)  // 标记为不完全成功，需要 Qwen 补充
                .partyARoleName(partyARole)
                .partyBRoleName(partyBRole)
                .errorMessage("已找到关键词，等待 Qwen 精确提取")
                .build();
        }

        log.warn("未找到关键词，将使用 Qwen 直接处理");

        // 返回空结果，供 Qwen 处理
        return PartyExtractionResponse.builder()
            .success(false)
            .errorMessage("本地未找到关键词，需要 Qwen 处理")
            .build();
    }

    /**
     * 清理提取的名称
     */
    private String cleanPartyName(String name) {
        if (name == null) {
            return null;
        }

        // 移除空白
        name = name.replaceAll("\\s+", "");

        // 移除前后括号
        name = name.replaceAll("^[（(]+", "").replaceAll("[）)]+$", "");

        // 只保留有效部分（公司名称通常是第一个分句）
        if (name.contains("，") || name.contains(",")) {
            name = name.split("[，,]")[0];
        }

        name = name.trim();

        // 验证长度
        if (name.isEmpty() || name.length() > 100) {
            return null;
        }

        return name;
    }

    /**
     * 为 Qwen 生成改进的提取提示词
     * 基于关键词位置提取的上下文，准确识别公司名称
     */
    private String generateQwenExtractionPrompt(PartyExtractionRequest request, PartyExtractionResponse localResult) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是专业的合同分析助手。请从以下合同片段中提取甲乙方的完整公司/组织名称。\n\n");

        if (request.getContractType() != null && !request.getContractType().isEmpty()) {
            prompt.append("【合同类型】\n");
            prompt.append(request.getContractType()).append("\n\n");
        }

        // 提示已识别的关键词角色
        if (localResult.getPartyARoleName() != null) {
            prompt.append("【已识别的关键词】\n");
            prompt.append("甲方角色标签：").append(localResult.getPartyARoleName()).append("\n");
        }
        if (localResult.getPartyBRoleName() != null) {
            prompt.append("乙方角色标签：").append(localResult.getPartyBRoleName()).append("\n");
        }
        prompt.append("\n");

        prompt.append("【合同文本片段】\n");
        prompt.append("---\n");
        prompt.append(request.getContractText());
        prompt.append("\n---\n\n");

        prompt.append("【任务说明】\n");
        prompt.append("请按照以下要求提取甲乙方信息：\n");
        prompt.append("1. 在文本中找到\"").append(localResult.getPartyARoleName() != null ? localResult.getPartyARoleName() : "甲方").append("\"关键词\n");
        prompt.append("2. 在该关键词后查找紧邻的公司/组织名称（通常是括号内或冒号后的文本）\n");
        prompt.append("3. 提取完整的名称，包括所有组成部分（如：XXX有限公司、XXX有限责任公司等）\n");
        prompt.append("4. 同样处理乙方信息\n");
        prompt.append("5. 如无法准确提取，返回从上下文推断出的最可能的公司名称\n\n");

        prompt.append("【命名规范】\n");
        prompt.append("- 甲方映射：甲方、买方、需方、发包人、客户、订购方、用户、委托方\n");
        prompt.append("- 乙方映射：乙方、卖方、供方、承包人、服务商、承接方、受托方\n\n");

        prompt.append("【返回格式（仅返回 JSON，不要包含任何其他文本）】\n");
        prompt.append("{\n");
        prompt.append("  \"partyA\": \"甲方的完整公司名称\",\n");
        prompt.append("  \"partyB\": \"乙方的完整公司名称\",\n");
        prompt.append("  \"partyARoleName\": \"甲方在合同中的角色标签\",\n");
        prompt.append("  \"partyBRoleName\": \"乙方在合同中的角色标签\",\n");
        prompt.append("  \"recommendedStance\": \"A 或 B\",\n");
        prompt.append("  \"stanceReason\": \"推荐该立场的简短原因\"\n");
        prompt.append("}\n\n");

        prompt.append("【重要提示】\n");
        prompt.append("- partyA 和 partyB 必须返回有效的公司名称，不能为 null\n");
        prompt.append("- 不要返回职位、个人名字或其他非公司名称的内容\n");
        prompt.append("- 仅返回 JSON，不要包含解释或其他文本\n");

        return prompt.toString();
    }

    /**
     * 生成合同方提取提示
     *
     * 只识别和返回甲乙两方，忽略丙方及以后的方
     * 支持各种同义标签的映射
     */
    private String generateExtractionPrompt(PartyExtractionRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请分析以下合同文本，严格按照以下规则识别甲方和乙方信息：\n\n");

        prompt.append("【识别规则】\n");
        prompt.append("1. 只识别和返回甲乙两方，如果出现丙方/丁方等其他方，完全忽略不返回\n");
        prompt.append("2. 如果合同中使用了不同的标签名（而非甲乙方），按以下规则映射：\n");
        prompt.append("   - 映射到 A：甲方、买方、需方、发包人、客户、订购方、用户、委托方\n");
        prompt.append("   - 映射到 B：乙方、卖方、供方、承包人、服务商、承接方、受托方\n");
        prompt.append("3. 优先级：明确的甲乙方标签 > 角色标签（如买方/卖方）\n");
        prompt.append("4. 只提取真实的公司/组织名称，不要提取职位名称或个人名字\n\n");

        if (request.getContractType() != null && !request.getContractType().isEmpty()) {
            prompt.append("合同类型：").append(request.getContractType()).append("\n\n");
        }

        prompt.append("【待分析的合同文本】\n");
        prompt.append("---\n");

        // 截取前 3000 个字以减少 token 消耗，但保留足够的上下文
        String text = request.getContractText();
        if (text.length() > 3000) {
            text = text.substring(0, 3000) + "\n...（文本过长，已截断）";
        }
        prompt.append(text);
        prompt.append("\n---\n\n");

        prompt.append("【返回要求】\n");
        prompt.append("严格遵循以下JSON格式返回，不要包含任何其他文本或说明：\n");
        prompt.append("{\n");
        prompt.append("  \"partyA\": \"第一方的完整公司名称（映射为A方的角色）\",\n");
        prompt.append("  \"partyB\": \"第二方的完整公司名称（映射为B方的角色）\",\n");
        prompt.append("  \"partyARoleName\": \"甲方/买方/需方/等原始角色名称\",\n");
        prompt.append("  \"partyBRoleName\": \"乙方/卖方/供方/等原始角色名称\",\n");
        prompt.append("  \"recommendedStance\": \"A或B，推荐用户选择哪一方进行审查\",\n");
        prompt.append("  \"stanceReason\": \"为什么推荐这个立场的简短原因（1-2句话）\"\n");
        prompt.append("}\n\n");

        prompt.append("【返回示例】\n");
        prompt.append("示例1（使用甲乙方标签）:\n");
        prompt.append("{\n");
        prompt.append("  \"partyA\": \"ABC采购有限公司\",\n");
        prompt.append("  \"partyB\": \"XYZ供应股份公司\",\n");
        prompt.append("  \"partyARoleName\": \"甲方（采购方）\",\n");
        prompt.append("  \"partyBRoleName\": \"乙方（供应方）\",\n");
        prompt.append("  \"recommendedStance\": \"A\",\n");
        prompt.append("  \"stanceReason\": \"甲方作为买方/采购方，需要重点关注产品质量、交付期限和违约责任条款。\"\n");
        prompt.append("}\n\n");

        prompt.append("示例2（使用买方/卖方标签）:\n");
        prompt.append("{\n");
        prompt.append("  \"partyA\": \"北京科技有限责任公司\",\n");
        prompt.append("  \"partyB\": \"上海建筑工程有限公司\",\n");
        prompt.append("  \"partyARoleName\": \"甲方（发包人/委托方）\",\n");
        prompt.append("  \"partyBRoleName\": \"乙方（承包人/承接方）\",\n");
        prompt.append("  \"recommendedStance\": \"B\",\n");
        prompt.append("  \"stanceReason\": \"乙方作为承包方，需要重点关注付款条件、工期延期和费用调整条款。\"\n");
        prompt.append("}\n\n");

        prompt.append("【重要提醒】\n");
        prompt.append("- 如果无法识别甲乙方，partyA和partyB返回null\n");
        prompt.append("- 只返回JSON，不要包含任何其他解释或文字\n");
        prompt.append("- 确保JSON格式严格正确，可以被标准JSON解析器解析\n");

        return prompt.toString();
    }

    /**
     * 从Qwen返回的文本中提取JSON
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        response = response.trim();

        // 尝试方式1: 提取```json...```代码块
        Pattern jsonBlockPattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher jsonBlockMatcher = jsonBlockPattern.matcher(response);
        if (jsonBlockMatcher.find()) {
            String jsonContent = jsonBlockMatcher.group(1).trim();
            log.debug("从代码块中提取JSON");
            return jsonContent;
        }

        // 尝试方式2: 查找第一个 { 和最后一个 }
        int firstBrace = response.indexOf('{');
        int lastBrace = response.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            String jsonContent = response.substring(firstBrace, lastBrace + 1);
            log.debug("从大括号中提取JSON");
            return jsonContent;
        }

        // 尝试方式3: 直接作为JSON
        if (response.startsWith("{") && response.endsWith("}")) {
            log.debug("响应本身就是JSON");
            return response;
        }

        log.warn("无法识别JSON格式");
        return null;
    }

    /**
     * 检查 Qwen 服务是否可用
     */
    public boolean isQwenAvailable() {
        try {
            java.util.Map<String, String> config = qwenClient.getConfig();
            String apiKey = config.getOrDefault("api-key", "");
            String baseUrl = config.getOrDefault("base-url", "");

            boolean available = !apiKey.isEmpty() && !baseUrl.isEmpty() && !apiKey.equals("sk-");

            if (available) {
                log.info("✓ Qwen服务可用");
            } else {
                log.warn("✗ Qwen服务不可用");
            }

            return available;
        } catch (Exception e) {
            log.warn("无法检查Qwen服务: {}", e.getMessage());
            return false;
        }
    }
}
