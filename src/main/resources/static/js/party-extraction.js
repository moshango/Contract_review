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
 *
 * 顺序显示流程：
 * 1. 首先隐藏所有加载动画
 * 2. 显示甲乙方信息区域 (identified-parties-info)
 * 3. 显示立场选择和审查方式区域 (review-options-section)
 *
 * 确保两个关键元素都正确显示：
 * - identified-parties-info：显示甲乙方信息
 * - review-options-section：显示立场选择和审查方式
 */
function displayPartyExtractionResult(extractionResult, contractType) {
    logger.log('【关键】displayPartyExtractionResult 被调用，开始顺序显示流程');

    // 步骤1：隐藏所有可能的加载动画元素
    const partyIdentificationLoading = document.getElementById('party-identification-loading');
    if (partyIdentificationLoading) {
        partyIdentificationLoading.style.display = 'none';
        logger.log('✅ 步骤1a: 已隐藏 party-identification-loading 加载动画');
    }

    const ruleReviewLoading = document.getElementById('rule-review-loading');
    if (ruleReviewLoading) {
        ruleReviewLoading.style.display = 'none';
        logger.log('✅ 步骤1b: 已隐藏 rule-review-loading 加载动画');
    }

    // 【关键修复】步骤1c：显示父容器 party-identification-section
    const parentSection = document.getElementById('party-identification-section');
    if (parentSection) {
        parentSection.style.display = 'block';
        logger.log('✅ 步骤1c: 【关键修复】已显示父容器 party-identification-section');
    }

    // 步骤2：显示甲乙方信息区域 (identified-parties-info)
    const partiesInfoDiv = document.getElementById('identified-parties-info');
    if (partiesInfoDiv) {
        partiesInfoDiv.style.display = 'block';
        partiesInfoDiv.style.visibility = 'visible';
        partiesInfoDiv.style.opacity = '1';
        partiesInfoDiv.style.zIndex = '100';
        logger.log('✅ 步骤2: 已显示甲乙方信息区域 (identified-parties-info)');

        // 【调试】检查元素状态
        logger.log('🔍 调试: identified-parties-info 元素状态:', {
            display: partiesInfoDiv.style.display,
            visibility: partiesInfoDiv.style.visibility,
            offsetHeight: partiesInfoDiv.offsetHeight,
            offsetWidth: partiesInfoDiv.offsetWidth,
            isVisible: partiesInfoDiv.offsetHeight > 0
        });
    }

    // 步骤2：更新甲乙方显示内容
    document.getElementById('identified-party-a').textContent = extractionResult.partyA || '(未识别)';
    document.getElementById('identified-party-b').textContent = extractionResult.partyB || '(未识别)';
    logger.log('✅ 步骤2: 已更新甲乙方显示内容: ' + extractionResult.partyA + ' / ' + extractionResult.partyB);

    // 【调试】验证内容是否正确设置
    const partyAElement = document.getElementById('identified-party-a');
    const partyBElement = document.getElementById('identified-party-b');
    logger.log('🔍 调试: 甲乙方元素内容验证:', {
        partyA: partyAElement ? partyAElement.textContent : 'NOT_FOUND',
        partyB: partyBElement ? partyBElement.textContent : 'NOT_FOUND'
    });

    // 步骤3：显示立场选择和审查方式区域 (review-options-section)
    const reviewOptionsSection = document.getElementById('review-options-section');
    if (reviewOptionsSection) {
        reviewOptionsSection.style.display = 'block';
        reviewOptionsSection.style.zIndex = '20';
        logger.log('✅ 步骤3: 已显示立场选择和审查方式区域 (review-options-section)');
    } else {
        logger.error('❌ 步骤3: 找不到 review-options-section 元素！');
    }

    // 验证原生立场选择UI是否可用
    const stanceRadioGroup = document.querySelector('[name="rule-review-stance"]');
    if (stanceRadioGroup) {
        logger.log('✅ 验证: 原生立场选择UI 可用');
    }

    // 保存提取结果供后续使用
    window.currentPartyExtractionResult = extractionResult;
    window.currentRuleReviewContractType = contractType;

    logger.log('✅ 【完成】顺序显示流程完成：甲乙方信息 → 立场选择和审查方式');
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

    // 确保 clauses 是有效的数组
    if (!clauses || !Array.isArray(clauses) || clauses.length === 0) {
        clausesDiv.innerHTML = '<p style="padding: 15px; color: #999;">未检出匹配的条款</p>';
        return;
    }

    clauses.forEach((clause, index) => {
        // 防御性编程：检查clause是否为空
        if (!clause) {
            return;
        }

        const riskColorMap = {
            'high': '#F44336',
            'medium': '#FF9800',
            'low': '#FFC107'
        };

        // 修复：安全地获取风险等级，支持多种字段名
        let riskLevel = 'low';
        if (clause.riskLevel) {
            riskLevel = String(clause.riskLevel).toLowerCase();
        } else if (clause.highestRisk) {
            riskLevel = String(clause.highestRisk).toLowerCase();
        }

        const riskColor = riskColorMap[riskLevel] || '#999';
        const matchedRuleCount = clause.matchedRuleCount || 0;
        const matchedRules = clause.matchedRules || [];

        html += `
            <div style="border-bottom: 1px solid #eee; padding: 15px; margin-bottom: 10px;">
                <div style="display: flex; align-items: center; margin-bottom: 10px;">
                    <span style="display: inline-block; width: 8px; height: 8px; background: ${riskColor}; border-radius: 50%; margin-right: 10px;"></span>
                    <strong style="font-size: 16px;">${clause.clauseId || '未知'} - ${clause.heading || '未知'}</strong>
                    <span style="margin-left: 10px; padding: 3px 8px; background: ${riskColor}; color: white; border-radius: 3px; font-size: 12px;">${riskLevel.toUpperCase()}</span>
                    <span style="margin-left: auto; color: #666; font-size: 12px;">${matchedRuleCount} 条规则匹配</span>
                </div>

                <div style="background: #f9f9f9; padding: 10px; border-left: 3px solid ${riskColor}; margin-bottom: 10px; border-radius: 2px;">
                    <div style="font-size: 13px; line-height: 1.6; color: #333;">
                        ${matchedRules.map(rule => {
                            if (!rule) return '';

                            let ruleRiskLevel = 'low';
                            if (rule.risk) {
                                ruleRiskLevel = String(rule.risk).toLowerCase();
                            } else if (rule.riskLevel) {
                                ruleRiskLevel = String(rule.riskLevel).toLowerCase();
                            }

                            const ruleRiskColor = riskColorMap[ruleRiskLevel] || '#999';
                            let keywords = [];
                            if (rule.matchedKeywords) {
                                keywords = Array.isArray(rule.matchedKeywords) ? rule.matchedKeywords : [String(rule.matchedKeywords)];
                            } else if (rule.keywords) {
                                keywords = Array.isArray(rule.keywords) ? rule.keywords : [String(rule.keywords)];
                            }

                            return `
                            <div style="margin-bottom: 12px;">
                                <strong style="color: ${ruleRiskColor};">【${ruleRiskLevel.toUpperCase()}】 ${rule.id || '规则'}</strong>
                                ${keywords.length > 0 ? `
                                    <div style="margin: 5px 0; font-size: 11px; color: #999;">
                                        🔍 匹配关键词: <span style="background: #ffffcc; padding: 2px 4px; border-radius: 2px;">${keywords.join(', ')}</span>
                                    </div>
                                ` : ''}
                                <p style="margin: 5px 0; font-size: 12px; color: #666;">${(rule.checklist || '').split('\n').join('<br>')}</p>
                            </div>
                        `}).join('')}
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

/**
 * 继续规则审查（用户选择立场后）
 */
async function proceedWithRuleReview() {
    if (!ruleReviewFile) {
        showToast('请先选择合同文件', 'error');
        return;
    }

    const stance = document.querySelector('input[name="rule-review-stance"]:checked').value;
    if (!stance) {
        showToast('请选择审查立场', 'error');
        return;
    }

    logger.log('✓ 用户选择规则审查立场:', stance);

    // 隐藏审查选项，显示加载
    document.getElementById('review-options-section').style.display = 'none';
    const loadingDiv = document.getElementById('rule-review-loading');
    loadingDiv.style.display = 'flex';
    loadingDiv.innerHTML = '<div class="spinner"></div><p>正在进行规则审查，请稍候...</p>';

    try {
        const contractType = document.getElementById('rule-review-contract-type').value;
        const formData = new FormData();
        formData.append('file', ruleReviewFile);
        formData.append('contractType', contractType);
        formData.append('party', stance);
        formData.append('reviewMode', 'rules');

        logger.log('调用规则审查接口', {file: ruleReviewFile.name, contractType, party: stance});

        // 【关键修复】保存立场到全局变量
        window.ruleReviewStance = stance;
        logger.log('✅ 【关键】proceedWithRuleReview 已保存审查立场:', window.ruleReviewStance);

        const response = await fetch('/api/unified/review', {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || '规则审查失败');
        }

        const data = await response.json();
        logger.log('✓ 规则审查完成', data);

        // 隐藏加载动画
        loadingDiv.style.display = 'none';

        if (data.success) {
            // 保存结果和parseResultId
            window.ruleReviewResult = data;
            window.ruleReviewParseResultId = data.parseResultId;
            logger.log('✅ 【关键】proceedWithRuleReview 已设置 parseResultId:', window.ruleReviewParseResultId);

            // 显示统计信息
            const stats = data.statistics || {};
            document.getElementById('stat-total-clauses').textContent = stats.totalClauses || 0;
            document.getElementById('stat-matched-clauses').textContent = stats.matchedClauses || 0;
            document.getElementById('stat-high-risk').textContent = stats.highRiskClauses || 0;
            document.getElementById('stat-total-rules').textContent = stats.totalRules || 0;

            // 显示风险分布
            const matchResults = data.matchResults || [];
            let riskCount = { high: 0, medium: 0, low: 0 };
            matchResults.forEach(result => {
                const riskLevel = result.riskLevel?.toLowerCase() || 'low';
                if (riskLevel in riskCount) {
                    riskCount[riskLevel]++;
                }
            });
            document.getElementById('risk-high').textContent = riskCount.high || 0;
            document.getElementById('risk-medium').textContent = riskCount.medium || 0;
            document.getElementById('risk-low').textContent = riskCount.low || 0;

            // 显示匹配的条款
            displayRuleReviewClauses(matchResults);

            // 显示Prompt
            const promptElement = document.getElementById('rule-review-prompt');
            if (promptElement) {
                promptElement.textContent = data.prompt || '';
            }

            // 显示结果
            document.getElementById('rule-review-result').style.display = 'block';

            showToast('✅ 规则审查完成！', 'success');
        } else {
            showToast('❌ 规则审查失败：' + (data.error || '未知错误'), 'error');
        }
    } catch (error) {
        logger.error('规则审查失败', error);
        loadingDiv.style.display = 'none';
        showToast('❌ 规则审查失败：' + error.message, 'error');
    }
}

/**
 * 继续一键审查（用户选择立场后）
 */
async function proceedWithOneClickReview() {
    if (!ruleReviewFile) {
        showToast('请先选择合同文件', 'error');
        return;
    }

    const stance = document.querySelector('input[name="rule-review-stance"]:checked').value;
    if (!stance) {
        showToast('请选择审查立场', 'error');
        return;
    }

    logger.log('✓ 用户选择一键审查立场:', stance);

    // 隐藏审查选项，显示加载
    document.getElementById('review-options-section').style.display = 'none';
    const loadingDiv = document.getElementById('rule-review-loading');
    loadingDiv.style.display = 'flex';
    loadingDiv.innerHTML = '<div class="spinner"></div><p>步骤 1/6: 正在解析合同...' +
                          '<br/>步骤 2/6: 正在进行规则匹配和生成Prompt...' +
                          '<br/>步骤 3/6: 正在调用Qwen进行审查...' +
                          '<br/>步骤 4/6: 正在生成批注...' +
                          '<br/>步骤 5/6: 正在保存文档...' +
                          '<br/>请稍候...</p>';

    try {
        const contractType = document.getElementById('rule-review-contract-type').value;
        const formData = new FormData();
        formData.append('file', ruleReviewFile);
        formData.append('stance', stance);

        logger.log('调用一键审查接口', {file: ruleReviewFile.name, stance});

        // 【关键修复】保存立场到全局变量
        window.ruleReviewStance = stance;
        logger.log('✅ 【关键】proceedWithOneClickReview 已保存审查立场:', window.ruleReviewStance);

        const response = await fetch('/api/qwen/rule-review/one-click-review', {
            method: 'POST',
            body: formData
        });

        logger.log('📥 收到响应，状态码:', response.status);

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || '一键审查失败');
        }

        // 一键审查返回文件流，直接下载
        const blob = await response.blob();
        const filename = ruleReviewFile.name.replace(/\.(docx|doc)$/i, '') + '_一键审查_' + stance + '.docx';

        logger.log('💾 下载文件:', filename);

        // 下载文件
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);

        // 隐藏加载动画
        loadingDiv.style.display = 'none';

        // 显示成功消息
        showToast('✅ 一键审查完成！文件已下载。同时已自动保存到文档中心。', 'success');

        // 显示完成提示（可选）
        const resultDiv = document.getElementById('rule-review-result');
        if (resultDiv) {
            resultDiv.style.display = 'block';
            const html = `
                <div style="background: #e8f5e9; border-left: 4px solid #4CAF50; padding: 15px; border-radius: 4px;">
                    <h3 style="color: #2e7d32; margin-top: 0;">✅ 一键审查成功</h3>
                    <p><strong>📄 文件:</strong> ${filename}</p>
                    <p><strong>👁️ 审查立场:</strong> ${stance}</p>
                    <p><strong>📍 保存位置:</strong> 文档中心/已生成的审查报告/</p>
                </div>
            `;
            resultDiv.innerHTML = html;
        }

    } catch (error) {
        logger.error('一键审查失败', error);
        loadingDiv.style.display = 'none';
        showToast('❌ 一键审查失败：' + error.message, 'error');
    }
}
