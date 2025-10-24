/**
 * 规则审查 - 合同方提取模块
 *
 * 工作流程：
 * 1. 文件上传后，先提取合同文本和类型
 * 2. 调用后端 /api/review/extract-parties 使用 Qwen 识别甲乙方
 * 3. 显示识别的甲乙方信息
 * 4. 用户选择立场后，调用 /api/review/analyze 进行规则审查
 */

/**
 * 第一步：上传文件后提取合同方信息
 */
async function extractRuleReviewParties() {
    const file = ruleReviewFile;
    const contractType = document.getElementById('rule-review-contract-type').value;

    if (!file) {
        showToast('请先选择合同文件', 'error');
        return;
    }

    // 显示加载动画
    const loadingDiv = document.getElementById('rule-review-loading');
    loadingDiv.style.display = 'flex';
    loadingDiv.innerHTML = '<div class="spinner"></div><p>正在识别合同方信息，请稍候...</p>';

    try {
        // 第一步：解析合同
        logger.log('步骤1: 解析合同文件');
        const parseFormData = new FormData();
        parseFormData.append('file', file);

        const parseResponse = await fetch('/api/parse?anchors=generate&returnMode=json', {
            method: 'POST',
            body: parseFormData
        });

        if (!parseResponse.ok) {
            const errorData = await parseResponse.json();
            throw new Error(errorData.error || '解析合同失败');
        }

        const parseResult = await parseResponse.json();
        logger.log('✓ 合同解析完成', parseResult);

        // 【关键修复】保存 parseResultId 用于后续批注
        // parseResultId 可能在顶级或在 meta 对象中
        let parseResultId = parseResult.parseResultId || (parseResult.meta && parseResult.meta.parseResultId);
        if (parseResultId) {
            window.ruleReviewParseResultId = parseResultId;
            logger.log('✅ 【关键】已保存 parseResultId:', window.ruleReviewParseResultId);
        } else {
            logger.log('⚠️ 响应中未包含 parseResultId');
        }

        // 检查是否已在文件解析时识别到甲乙方信息
        if (parseResult.partyA && parseResult.partyB) {
            logger.log('✓ 文件解析时已识别甲乙方: A=' + parseResult.partyA + ', B=' + parseResult.partyB);

            // 直接使用已识别的信息，无需调用 Qwen
            const extractionResult = {
                success: true,
                partyA: parseResult.partyA,
                partyB: parseResult.partyB,
                partyARoleName: parseResult.partyARoleName || '甲方',
                partyBRoleName: parseResult.partyBRoleName || '乙方',
                recommendedStance: 'A',
                stanceReason: '根据合同内容，甲方通常需要关注更多风险条款'
            };

            loadingDiv.style.display = 'none';
            displayPartyExtractionResult(extractionResult, contractType);
            return;
        }

        // 如果未识别到甲乙方，则需要调用 Qwen 提取
        logger.log('文件解析未识别甲乙方，调用 Qwen 进行识别...');

        // 提取合同文本：优先使用 fullContractText（包含甲乙方），否则合并条款文本
        let contractText = '';
        if (parseResult.fullContractText) {
            // 使用完整合同文本（包含甲乙方信息）
            contractText = parseResult.fullContractText;
            logger.log('✓ 使用完整合同文本（包含甲乙方信息）');
        } else if (parseResult.clauses && parseResult.clauses.length > 0) {
            // 备选：合并所有条款文本
            contractText = parseResult.clauses
                .map(c => (c.heading ? c.heading + '\n' : '') + (c.text || ''))
                .join('\n\n');
            logger.log('⚠ 使用条款文本（未包含甲乙方信息）');
        }

        if (contractText.length > 3000) {
            contractText = contractText.substring(0, 3000);
            logger.log('合同文本长度超过3000字，已截断');
        }

        logger.log('提取合同文本长度: ' + contractText.length);

        // 第二步：调用后端提取合同方
        logger.log('步骤2: 使用 Qwen 识别甲乙方');
        const extractionResponse = await fetch('/api/review/extract-parties', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                contractText: contractText,
                contractType: contractType,
                parseResultId: null
            })
        });

        if (!extractionResponse.ok) {
            const errorData = await extractionResponse.json();
            throw new Error(errorData.error || '识别合同方失败');
        }

        const extractionResult = await extractionResponse.json();
        logger.log('✓ 合同方识别完成', extractionResult);

        // 隐藏加载动画
        loadingDiv.style.display = 'none';

        if (extractionResult.success && extractionResult.partyA && extractionResult.partyB) {
            // 显示识别结果
            displayPartyExtractionResult(extractionResult, contractType);
        } else {
            showToast('无法识别合同方信息: ' + (extractionResult.error || '未知错误'), 'error');
            logger.error('合同方识别失败', extractionResult);
        }

    } catch (error) {
        logger.error('提取合同方信息失败', error);
        showToast('提取合同方失败: ' + error.message, 'error');
        document.getElementById('rule-review-loading').style.display = 'none';
    }
}

/**
 * 显示识别的合同方信息，让用户选择立场
 */
function displayPartyExtractionResult(extractionResult, contractType) {
    // 隐藏原来的立场选择
    const stanceRadioGroup = document.querySelector('[name="rule-review-stance"]').parentElement.parentElement;
    stanceRadioGroup.style.display = 'none';

    // 显示识别的合同方信息
    const partiesInfoDiv = document.getElementById('identified-parties-info');
    partiesInfoDiv.style.display = 'block';

    // 更新显示的内容
    document.getElementById('identified-party-a').innerHTML =
        `<strong>${extractionResult.partyA}</strong><br/><span style="font-size: 12px; color: #666;">(${extractionResult.partyARoleName})</span>`;

    document.getElementById('identified-party-b').innerHTML =
        `<strong>${extractionResult.partyB}</strong><br/><span style="font-size: 12px; color: #666;">(${extractionResult.partyBRoleName})</span>`;

    // 添加立场选择按钮
    let stanceButtonsHTML = `
        <div style="margin-top: 15px; padding: 15px; background: #f5f5f5; border-radius: 4px;">
            <p style="margin: 0 0 10px 0; font-weight: bold; color: #333;">请选择您的立场：</p>
            <div style="display: flex; gap: 15px;">
                <button class="btn btn-primary" onclick="selectRuleReviewStance('A')"
                        style="flex: 1; background: #E3F2FD; color: #1976D2; border: 2px solid #1976D2;">
                    <span>选择甲方立场</span><br/>
                    <span style="font-size: 12px;">${extractionResult.partyA}</span>
                </button>
                <button class="btn btn-primary" onclick="selectRuleReviewStance('B')"
                        style="flex: 1; background: #F3E5F5; color: #7B1FA2; border: 2px solid #7B1FA2;">
                    <span>选择乙方立场</span><br/>
                    <span style="font-size: 12px;">${extractionResult.partyB}</span>
                </button>
            </div>
            <p style="font-size: 12px; color: #666; margin: 10px 0 0 0; font-style: italic;">
                💡 提示：${extractionResult.stanceReason || '根据您的身份选择对应的立场获得更准确的审查建议'}
            </p>
        </div>
    `;

    // 在识别信息后插入立场选择按钮
    const insertionPoint = partiesInfoDiv.nextElementSibling;
    if (insertionPoint && insertionPoint.id === 'party-stance-buttons') {
        insertionPoint.innerHTML = stanceButtonsHTML;
    } else {
        const stanceButtonDiv = document.createElement('div');
        stanceButtonDiv.id = 'party-stance-buttons';
        stanceButtonDiv.innerHTML = stanceButtonsHTML;
        partiesInfoDiv.parentNode.insertBefore(stanceButtonDiv, insertionPoint);
    }

    // 保存提取结果供后续使用
    window.currentPartyExtractionResult = extractionResult;
    window.currentRuleReviewContractType = contractType;
}

/**
 * 用户选择立场后，继续进行规则审查
 */
async function selectRuleReviewStance(stance) {
    if (!window.currentPartyExtractionResult || !ruleReviewFile) {
        showToast('数据丢失，请重新上传文件', 'error');
        return;
    }

    const contractType = window.currentRuleReviewContractType;
    const file = ruleReviewFile;

    // 隐藏立场选择，显示加载
    document.getElementById('party-stance-buttons').style.display = 'none';
    const loadingDiv = document.getElementById('rule-review-loading');
    loadingDiv.style.display = 'flex';
    loadingDiv.innerHTML = '<div class="spinner"></div><p>正在进行规则审查，请稍候...</p>';

    try {
        logger.log(`✓ 用户选择立场: ${stance}`);

        // 构建FormData
        const formData = new FormData();
        formData.append('file', file);
        formData.append('contractType', contractType);
        formData.append('party', stance);

        // 调用规则审查分析接口
        const analysisResponse = await fetch('/api/review/analyze', {
            method: 'POST',
            body: formData
        });

        if (!analysisResponse.ok) {
            const errorData = await analysisResponse.json();
            throw new Error(errorData.error || '规则审查失败');
        }

        const analysisResult = await analysisResponse.json();
        logger.log('✓ 规则审查完成', analysisResult);

        // 隐藏加载动画
        loadingDiv.style.display = 'none';

        // 显示规则审查结果
        displayRuleReviewResults(analysisResult);

        showToast('规则审查完成！', 'success');

    } catch (error) {
        logger.error('规则审查失败', error);
        showToast('规则审查失败: ' + error.message, 'error');
        document.getElementById('rule-review-loading').style.display = 'none';
        document.getElementById('party-stance-buttons').style.display = 'block';
    }
}

/**
 * 显示规则审查结果
 * 显示匹配的条款、风险分布和生成的Prompt
 */
function displayRuleReviewResults(analysisResult) {
    // 更新统计信息
    document.getElementById('stat-total-clauses').textContent = analysisResult.statistics.totalClauses;
    document.getElementById('stat-matched-clauses').textContent = analysisResult.statistics.matchedClauses;
    document.getElementById('stat-high-risk').textContent = analysisResult.statistics.highRiskClauses;
    document.getElementById('stat-total-rules').textContent = analysisResult.statistics.totalMatchedRules;

    // 更新风险分布
    document.getElementById('risk-high').textContent = analysisResult.guidance.riskDistribution.high;
    document.getElementById('risk-medium').textContent = analysisResult.guidance.riskDistribution.medium;
    document.getElementById('risk-low').textContent = analysisResult.guidance.riskDistribution.low;

    // 显示匹配的条款
    displayRuleReviewClauses(analysisResult.matchResults);

    // 显示 Prompt
    document.getElementById('rule-review-prompt').textContent = analysisResult.prompt;

    // 显示结果
    document.getElementById('rule-review-result').style.display = 'block';

    // 保存审查结果
    window.ruleReviewResult = analysisResult;

    // 【重要】保留之前保存的 parseResultId，如果分析结果中有新的则使用新的
    // 这样可以确保 parseResultId 在整个审查流程中持久保存
    if (analysisResult.parseResultId) {
        window.ruleReviewParseResultId = analysisResult.parseResultId;
        logger.log('✓ 已保存新的 parseResultId: ' + analysisResult.parseResultId);
    } else if (window.ruleReviewParseResultId) {
        // 如果分析结果中没有 parseResultId，保留之前保存的值
        logger.log('✓ 保持之前的 parseResultId: ' + window.ruleReviewParseResultId);
    } else {
        logger.log('⚠️ 未获取到 parseResultId');
    }
}

/**
 * 显示规则审查匹配的条款
 * 复用自 main.js 的 displayRuleReviewClauses 函数
 */
function displayRuleReviewClauses(clauses) {
    const clausesDiv = document.getElementById('rule-review-clauses');
    let html = '';

    clauses.forEach((clause, index) => {
        const riskColorMap = {
            'high': '#F44336',
            'medium': '#FF9800',
            'low': '#FFC107'
        };
        const riskColor = riskColorMap[clause.riskLevel] || '#999';

        html += `
            <div style="border-bottom: 1px solid #eee; padding: 15px; margin-bottom: 10px;">
                <div style="display: flex; align-items: center; margin-bottom: 10px;">
                    <span style="display: inline-block; width: 8px; height: 8px; background: ${riskColor}; border-radius: 50%; margin-right: 10px;"></span>
                    <strong style="font-size: 16px;">${clause.clauseId} - ${clause.heading}</strong>
                    <span style="margin-left: 10px; padding: 3px 8px; background: ${riskColor}; color: white; border-radius: 3px; font-size: 12px;">${clause.riskLevel.toUpperCase()}</span>
                    <span style="margin-left: auto; color: #666; font-size: 12px;">${clause.matchedRuleCount} 条规则匹配</span>
                </div>

                <div style="background: #f9f9f9; padding: 10px; border-left: 3px solid ${riskColor}; margin-bottom: 10px; border-radius: 2px;">
                    <div style="font-size: 13px; line-height: 1.6; color: #333;">
                        ${clause.matchedRules.map(rule => `
                            <div style="margin-bottom: 12px;">
                                <strong style="color: ${riskColor};">【${rule.risk.toUpperCase()}】 ${rule.id || '规则'}</strong>
                                ${rule.matchedKeywords ? `
                                    <div style="margin: 5px 0; font-size: 11px; color: #999;">
                                        🔍 匹配关键词: <span style="background: #ffffcc; padding: 2px 4px; border-radius: 2px;">${rule.matchedKeywords.join(', ')}</span>
                                    </div>
                                ` : ''}
                                <p style="margin: 5px 0; font-size: 12px; color: #666;">${rule.checklist.split('\n').join('<br>')}</p>
                            </div>
                        `).join('')}
                    </div>
                </div>
            </div>
        `;
    });

    clausesDiv.innerHTML = html || '<p style="padding: 15px; color: #999;">未检出匹配的条款</p>';
}

/**
 * 简单的日志对象（用于调试）
 */
const logger = {
    log: function(message, data) {
        console.log('[RuleReview]', message, data || '');
    },
    warn: function(message, data) {
        console.warn('[RuleReview]', message, data || '');
    },
    error: function(message, error) {
        console.error('[RuleReview]', message, error || '');
    }
};
