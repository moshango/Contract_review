// ===== 【新增】审查立场功能 =====

/**
 * 获取当前选择的审查立场
 * @returns {string} 立场值：'A'(甲方)、'B'(乙方) 或 'Neutral'(中立)
 */
function getSelectedStance() {
    const stanceRadios = document.querySelectorAll('input[name="rule-review-stance"]');
    for (const radio of stanceRadios) {
        if (radio.checked) {
            return radio.value;
        }
    }
    return 'Neutral'; // 默认中立
}

/**
 * 设置用户的审查立场（后端存储）
 * @param {string} stance - 立场值：'A'、'B' 或 ''(中立)
 */
async function setUserStance(stance) {
    try {
        const url = stance ? `/api/review/settings?party=${stance}` : `/api/review/settings`;
        const response = await fetch(url, {
            method: 'POST'
        });

        if (!response.ok) {
            console.warn('⚠️ 设置审查立场失败:', response.statusText);
            return false;
        }

        const data = await response.json();
        console.log('✅ 审查立场已设置:', data.stanceDescription);
        return true;
    } catch (error) {
        console.error('设置立场出错:', error);
        return false;
    }
}

/**
 * 获取当前的审查立场
 */
async function getCurrentStance() {
    try {
        const response = await fetch(`/api/review/settings`, {
            method: 'GET'
        });

        if (!response.ok) {
            return null;
        }

        const data = await response.json();
        console.log('当前审查立场:', data.stanceDescription);
        return data.currentStance;
    } catch (error) {
        console.error('获取立场出错:', error);
        return null;
    }
}

/**
 * 覆盖原始的 startRuleReview 函数 - 集成立场功能
 */
const originalStartRuleReview = startRuleReview;
async function startRuleReview() {
    if (!ruleReviewFile) {
        showToast('请先选择合同文件', 'error');
        return;
    }

    const contractType = document.getElementById('rule-review-contract-type').value;
    const stance = getSelectedStance(); // 【新增】获取用户选择的立场

    showLoading('rule-review');
    document.getElementById('rule-review-result').style.display = 'none';

    const formData = new FormData();
    formData.append('file', ruleReviewFile);

    try {
        // 【新增】先设置用户立场
        if (stance !== 'Neutral') {
            await setUserStance(stance);
        }

        // 【关键修改】在URL中添加party参数
        const encodedContractType = encodeURIComponent(contractType);
        const encodedStance = encodeURIComponent(stance);
        const url = `/api/review/analyze?contractType=${encodedContractType}&party=${encodedStance}`;

        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.error || '规则审查失败');
        }

        const data = await response.json();
        ruleReviewResult = data;

        // 【新增】显示用户的审查立场
        console.log('📊 审查立场:', data.stanceDescription);
        showToast(`✅ 已按${data.stanceDescription}的立场进行审查`, 'success');

        // 【关键修复】保存 parseResultId 供后续批注使用
        if (data.parseResultId) {
            ruleReviewParseResultId = data.parseResultId;
            console.log('✅ 【关键】已保存 parseResultId:', ruleReviewParseResultId);
            console.log('   使用 window.ruleReviewParseResultId 可在控制台查看');
            showToast('✅ 已生成 parseResultId，可用于后续批注', 'success');
        } else {
            console.warn('⚠️ 响应中未包含 parseResultId，后续批注可能不精确');
            ruleReviewParseResultId = null;
        }

        // 更新统计信息
        document.getElementById('stat-total-clauses').textContent = data.statistics.totalClauses;
        document.getElementById('stat-matched-clauses').textContent = data.statistics.matchedClauses;
        document.getElementById('stat-high-risk').textContent = data.statistics.highRiskClauses;
        document.getElementById('stat-total-rules').textContent = data.statistics.totalMatchedRules;

        // 更新风险分布
        document.getElementById('risk-high').textContent = data.guidance.riskDistribution.high;
        document.getElementById('risk-medium').textContent = data.guidance.riskDistribution.medium;
        document.getElementById('risk-low').textContent = data.guidance.riskDistribution.low;

        // 显示匹配的条款
        displayRuleReviewClauses(data.matchResults);

        // 显示 Prompt
        document.getElementById('rule-review-prompt').textContent = data.prompt;

        // 显示结果
        document.getElementById('rule-review-result').style.display = 'block';
        document.getElementById('rule-review-loading').style.display = 'none';

        showToast('规则审查完成!', 'success');
    } catch (error) {
        console.error('规则审查错误:', error);
        showToast('规则审查失败: ' + error.message, 'error');
    } finally {
        hideLoading('rule-review');
    }
}

// ===== 审查立场功能结束 =====
