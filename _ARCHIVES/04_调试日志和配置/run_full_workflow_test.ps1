# 完整工作流自测试脚本 (PowerShell)
# 用于验证 8080 ChatGPT 集成模块的批注功能

$ErrorActionPreference = "Stop"

Write-Host "==========================================" -ForegroundColor Green
Write-Host "开始完整工作流自测试" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""

$TEST_FILE = "D:\工作\合同审查系统开发\spring boot\Contract_review\测试合同_综合测试版.docx"
$BASE_URL = "http://localhost:8080"
$OUTPUT_FILE = "annotated_output.docx"

# 检查测试文件
if (!(Test-Path $TEST_FILE)) {
    Write-Host "❌ 测试文件不存在: $TEST_FILE" -ForegroundColor Red
    exit 1
}

Write-Host "✅ 测试文件存在" -ForegroundColor Green
Write-Host "📁 文件: $TEST_FILE" -ForegroundColor Cyan
Write-Host ""

# ============================================================
# 步骤 1: Parse 阶段 - 生成 Prompt 并获取锚点
# ============================================================
Write-Host "========== 步骤 1: Parse 阶段 ==========" -ForegroundColor Yellow
Write-Host "调用 /generate-prompt 端点..." -ForegroundColor Cyan
Write-Host ""

try {
    $PARSE_RESPONSE = Invoke-RestMethod -Uri "${BASE_URL}/chatgpt/generate-prompt" `
        -Method Post `
        -Form @{
            file = Get-Item $TEST_FILE
            contractType = "通用合同"
            anchors = "generate"
        } | ConvertTo-Json

    Write-Host "📋 Parse 响应:" -ForegroundColor Cyan
    Write-Host $PARSE_RESPONSE -ForegroundColor Gray
    Write-Host ""

    # 解析响应
    $response = $PARSE_RESPONSE | ConvertFrom-Json
    $PARSE_ID = $response.parseResultId
    $CLAUSE_COUNT = $response.clauseCount

    if ([string]::IsNullOrEmpty($PARSE_ID)) {
        Write-Host "❌ 未能获取 parseResultId" -ForegroundColor Red
        exit 1
    }

    Write-Host "✅ Parse 成功" -ForegroundColor Green
    Write-Host "   - parseResultId: $PARSE_ID" -ForegroundColor Cyan
    Write-Host "   - 条款数: $CLAUSE_COUNT" -ForegroundColor Cyan
    Write-Host ""

} catch {
    Write-Host "❌ Parse 调用失败: $_" -ForegroundColor Red
    exit 1
}

# ============================================================
# 步骤 2: 生成测试审查结果 JSON
# ============================================================
Write-Host "========== 步骤 2: 生成测试审查结果 ==========" -ForegroundColor Yellow

$REVIEW_JSON = @{
    issues = @(
        @{
            clauseId = "c1"
            anchorId = "anc-c1-304286e3"
            severity = "HIGH"
            category = "合同目的"
            finding = "需要更明确的合作范围定义"
            suggestion = "建议在合同目的中明确列出所有合作项目的具体范围和目标"
            targetText = "本合同旨在明确双方在软件开发、技术交付、知识产权、数据安全、保密义务及后期维护等方面的权利与义务，以确保项目顺利进行。"
            matchPattern = "EXACT"
        },
        @{
            clauseId = "c3"
            anchorId = "anc-c3-ea3ec6c4"
            severity = "MEDIUM"
            category = "交付物定义"
            finding = "前端展示界面缺少具体的技术规格要求"
            suggestion = "应明确前端展示的必需功能模块、性能要求（如响应时间）和兼容性要求"
            targetText = "（3）前端展示与标注界面（Web版）"
            matchPattern = "CONTAINS"
        },
        @{
            clauseId = "c5"
            severity = "HIGH"
            category = "付款条款"
            finding = "首付款比例偏低，风险较大"
            suggestion = "建议提高首付款比例至 40-50%，以平衡双方风险"
            targetText = "首付款：合同签订后7个工作日内支付30%"
            matchPattern = "CONTAINS"
        },
        @{
            clauseId = "c10"
            severity = "MEDIUM"
            category = "知识产权"
            finding = "知识产权所有权分配不够平衡"
            suggestion = "建议明确区分通用技术和项目特定技术的所有权归属"
            targetText = "所有项目成果的知识产权归甲方所有"
            matchPattern = "CONTAINS"
        },
        @{
            clauseId = "c15"
            severity = "HIGH"
            category = "违约责任"
            finding = "违约责任条款过于宽泛，缺少具体的量化标准"
            suggestion = "建议明确违约赔偿的计算方式和上限金额"
            targetText = "任一方违反合同约定，须赔偿对方因此造成的全部经济损失"
            matchPattern = "CONTAINS"
        }
    )
    summary = @{
        totalIssues = 5
        highRisk = 3
        mediumRisk = 2
        lowRisk = 0
        recommendation = "该合同在整体结构上较为完整，但在合作范围定义、付款风险管理和违约责任量化等方面仍有改进空间。建议重点关注高风险条款，通过进一步谈判和修改来平衡双方权益。"
    }
} | ConvertTo-Json -Depth 10

Write-Host "✅ 测试审查结果生成完成" -ForegroundColor Green
Write-Host "   - 问题数: 5" -ForegroundColor Cyan
Write-Host "   - 高风险: 3" -ForegroundColor Cyan
Write-Host "   - 中风险: 2" -ForegroundColor Cyan
Write-Host ""

# ============================================================
# 步骤 3: Annotate 阶段 - 导入审查结果并生成批注
# ============================================================
Write-Host "========== 步骤 3: Annotate 阶段 ==========" -ForegroundColor Yellow
Write-Host "调用 /import-result-xml 端点..." -ForegroundColor Cyan
Write-Host ""

try {
    $params = @{
        Uri = "${BASE_URL}/chatgpt/import-result-xml?parseResultId=${PARSE_ID}&anchorStrategy=preferAnchor&cleanupAnchors=false"
        Method = "Post"
        Form = @{
            chatgptResponse = $REVIEW_JSON
        }
        OutFile = $OUTPUT_FILE
    }

    Invoke-RestMethod @params

    if (Test-Path $OUTPUT_FILE) {
        Write-Host "✅ Annotate 成功" -ForegroundColor Green
        $fileSize = (Get-Item $OUTPUT_FILE).Length / 1KB
        Write-Host "   - 输出文件: $OUTPUT_FILE" -ForegroundColor Cyan
        Write-Host "   - 文件大小: $([math]::Round($fileSize, 2)) KB" -ForegroundColor Cyan
        Write-Host ""
    } else {
        Write-Host "❌ Annotate 失败" -ForegroundColor Red
        exit 1
    }

} catch {
    Write-Host "❌ Annotate 调用失败: $_" -ForegroundColor Red
    exit 1
}

# ============================================================
# 步骤 4: 验证结果
# ============================================================
Write-Host "========== 步骤 4: 验证结果 ==========" -ForegroundColor Yellow
Write-Host "检查输出文件..." -ForegroundColor Cyan
Write-Host ""

if (Test-Path $OUTPUT_FILE) {
    Write-Host "✅ 批注文档已生成" -ForegroundColor Green
    $fullPath = (Get-Item $OUTPUT_FILE).FullName
    Write-Host "   文件位置: $fullPath" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "📋 工作流完成！" -ForegroundColor Green
    Write-Host ""
    Write-Host "下一步建议:" -ForegroundColor Yellow
    Write-Host "1. 打开 $OUTPUT_FILE 文件检查批注" -ForegroundColor Cyan
    Write-Host "2. 验证批注位置是否正确" -ForegroundColor Cyan
    Write-Host "3. 查看后台日志中的关键信息" -ForegroundColor Cyan
} else {
    Write-Host "❌ 输出文件生成失败" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "✅ 完整工作流自测试结束" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
