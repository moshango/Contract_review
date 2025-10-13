// 全局变量
let parseFile = null;
let annotateFile = null;
let autoReviewFile = null;

// 切换选项卡
function switchTab(tabName) {
    // 移除所有活动状态
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    document.querySelectorAll('.panel').forEach(panel => {
        panel.classList.remove('active');
    });

    // 激活选中的选项卡
    event.target.classList.add('active');
    document.getElementById(tabName + '-panel').classList.add('active');
}

// 处理解析文件选择
function handleParseFileSelect(input) {
    const file = input.files[0];
    if (file) {
        parseFile = file;
        const fileNameSpan = document.getElementById('parse-file-name');
        fileNameSpan.textContent = file.name;
        fileNameSpan.classList.add('selected');
    }
}

// 处理批注文件选择
function handleAnnotateFileSelect(input) {
    const file = input.files[0];
    if (file) {
        annotateFile = file;
        const fileNameSpan = document.getElementById('annotate-file-name');
        fileNameSpan.textContent = file.name;
        fileNameSpan.classList.add('selected');
    }
}

// 处理返回模式变化
function handleReturnModeChange() {
    const returnMode = document.getElementById('return-mode').value;
    const anchorMode = document.getElementById('anchor-mode');

    // 如果选择file或both模式,锚点模式不能是none
    if ((returnMode === 'file' || returnMode === 'both') && anchorMode.value === 'none') {
        anchorMode.value = 'generate';
        showToast('已自动设置锚点模式为"生成锚点"', 'warning');
    }
}

// 解析合同
async function parseContract() {
    if (!parseFile) {
        showToast('请先选择合同文件', 'error');
        return;
    }

    const anchorMode = document.getElementById('anchor-mode').value;
    const returnMode = document.getElementById('return-mode').value;

    // 显示加载动画
    showLoading('parse');
    hideResult('parse');

    // 构建FormData
    const formData = new FormData();
    formData.append('file', parseFile);

    try {
        const url = `/parse?anchors=${anchorMode}&returnMode=${returnMode}`;
        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || '解析失败');
        }

        // 根据返回模式处理响应
        if (returnMode === 'json') {
            const data = await response.json();
            showParseResult(data);
            showToast('解析成功!', 'success');
        } else if (returnMode === 'file' || returnMode === 'both') {
            const blob = await response.blob();
            const filename = `parsed-${parseFile.name}`;
            downloadFile(blob, filename);
            showToast('解析成功! 文档已下载', 'success');

            // 如果是both模式,提示用户
            if (returnMode === 'both') {
                setTimeout(() => {
                    showToast('JSON结果可通过API直接获取', 'warning');
                }, 2000);
            }
        }
    } catch (error) {
        console.error('解析错误:', error);
        showToast('解析失败: ' + error.message, 'error');
    } finally {
        hideLoading('parse');
    }
}

// 批注合同
async function annotateContract() {
    if (!annotateFile) {
        showToast('请先选择合同文件', 'error');
        return;
    }

    const reviewJson = document.getElementById('review-json').value.trim();
    if (!reviewJson) {
        showToast('请输入审查结果JSON', 'error');
        return;
    }

    // 验证JSON格式
    try {
        JSON.parse(reviewJson);
    } catch (e) {
        showToast('审查结果JSON格式错误', 'error');
        return;
    }

    const anchorStrategy = document.getElementById('anchor-strategy').value;
    const cleanupAnchors = document.getElementById('cleanup-anchors').checked;

    // 显示加载动画
    showLoading('annotate');
    hideResult('annotate');

    // 构建FormData
    const formData = new FormData();
    formData.append('file', annotateFile);
    formData.append('review', reviewJson);

    try {
        const url = `/annotate?anchorStrategy=${anchorStrategy}&cleanupAnchors=${cleanupAnchors}`;
        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || '批注失败');
        }

        // 下载批注后的文件
        const blob = await response.blob();
        const filename = annotateFile.name.replace('.docx', '-annotated.docx');
        downloadFile(blob, filename);

        showAnnotateResult();
        showToast('批注成功! 文档已下载', 'success');
    } catch (error) {
        console.error('批注错误:', error);
        showToast('批注失败: ' + error.message, 'error');
    } finally {
        hideLoading('annotate');
    }
}

// 显示解析结果
function showParseResult(data) {
    const resultBox = document.getElementById('parse-result');
    const resultContent = document.getElementById('parse-result-content');

    resultContent.textContent = JSON.stringify(data, null, 2);
    resultBox.style.display = 'block';
}

// 显示批注结果
function showAnnotateResult() {
    const resultBox = document.getElementById('annotate-result');
    resultBox.style.display = 'block';
}

// 隐藏结果
function hideResult(type) {
    document.getElementById(type + '-result').style.display = 'none';
}

// 显示加载动画
function showLoading(type) {
    document.getElementById(type + '-loading').style.display = 'block';
}

// 隐藏加载动画
function hideLoading(type) {
    document.getElementById(type + '-loading').style.display = 'none';
}

// 复制解析结果
function copyParseResult() {
    const resultContent = document.getElementById('parse-result-content');
    const text = resultContent.textContent;

    navigator.clipboard.writeText(text).then(() => {
        showToast('结果已复制到剪贴板', 'success');
    }).catch(err => {
        console.error('复制失败:', err);
        showToast('复制失败', 'error');
    });
}

// 重置批注表单
function resetAnnotateForm() {
    document.getElementById('annotate-file').value = '';
    document.getElementById('annotate-file-name').textContent = '仅支持 .docx 格式';
    document.getElementById('annotate-file-name').classList.remove('selected');
    document.getElementById('review-json').value = '';
    annotateFile = null;
    hideResult('annotate');
}

// 下载文件
function downloadFile(blob, filename) {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
}

// 显示消息提示
function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = `toast ${type} show`;

    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    console.log('AI 合同审查助手已加载');

    // 检查API健康状态
    checkHealth();
});

// 检查API健康状态
async function checkHealth() {
    try {
        const response = await fetch('/health');
        const data = await response.json();
        console.log('API状态:', data);

        if (data.status === 'UP') {
            console.log('✅ 系统运行正常');
        }
    } catch (error) {
        console.error('❌ 无法连接到后端API:', error);
        showToast('无法连接到后端服务,请检查服务是否运行', 'error');
    }
}

// 拖拽上传支持
function setupDragAndDrop() {
    const panels = document.querySelectorAll('.panel');

    panels.forEach(panel => {
        panel.addEventListener('dragover', (e) => {
            e.preventDefault();
            panel.style.backgroundColor = '#f0f8ff';
        });

        panel.addEventListener('dragleave', (e) => {
            e.preventDefault();
            panel.style.backgroundColor = '';
        });

        panel.addEventListener('drop', (e) => {
            e.preventDefault();
            panel.style.backgroundColor = '';

            const files = e.dataTransfer.files;
            if (files.length > 0) {
                const file = files[0];

                // 判断是哪个面板
                if (panel.id === 'parse-panel') {
                    if (file.name.endsWith('.docx') || file.name.endsWith('.doc')) {
                        parseFile = file;
                        document.getElementById('parse-file-name').textContent = file.name;
                        document.getElementById('parse-file-name').classList.add('selected');
                        showToast('文件已添加: ' + file.name, 'success');
                    } else {
                        showToast('请上传 .docx 或 .doc 格式文件', 'error');
                    }
                } else if (panel.id === 'annotate-panel') {
                    if (file.name.endsWith('.docx')) {
                        annotateFile = file;
                        document.getElementById('annotate-file-name').textContent = file.name;
                        document.getElementById('annotate-file-name').classList.add('selected');
                        showToast('文件已添加: ' + file.name, 'success');
                    } else {
                        showToast('请上传 .docx 格式文件', 'error');
                    }
                }
            }
        });
    });
}

// 初始化拖拽上传
setTimeout(() => {
    setupDragAndDrop();
}, 100);

// ========== 审查标准管理功能 ==========

// 加载审查标准
async function loadReviewStandards() {
    const contractType = document.getElementById('contract-type-select').value;

    showLoading('standards');
    hideResult('standards');

    try {
        const response = await fetch(`/standards/contract-type/${contractType}`);

        if (!response.ok) {
            throw new Error('获取审查标准失败');
        }

        const standard = await response.json();
        showStandardsResult(standard);
        showToast('审查标准加载成功', 'success');
    } catch (error) {
        console.error('加载审查标准失败:', error);
        showToast('加载审查标准失败: ' + error.message, 'error');
    } finally {
        hideLoading('standards');
    }
}

// 根据类型加载标准
function loadStandardsByType() {
    // 可以在这里添加自动加载逻辑
    console.log('合同类型已切换');
}

// 显示审查标准结果
function showStandardsResult(standard) {
    const resultBox = document.getElementById('standards-result');
    const contentDiv = document.getElementById('standards-content');

    let html = `
        <div class="standard-info">
            <h4>📋 ${standard.name}</h4>
            <p><strong>类型:</strong> ${standard.contractType}</p>
            <p><strong>描述:</strong> ${standard.description}</p>
            <p><strong>版本:</strong> ${standard.version}</p>
            <p><strong>创建时间:</strong> ${standard.createdAt}</p>
        </div>

        <div class="rules-section">
            <h4>📝 审查规则 (${standard.rules.length}条)</h4>
    `;

    // 按风险等级分组显示规则
    const rulesBySeverity = {
        'HIGH': standard.rules.filter(r => r.severity === 'HIGH'),
        'MEDIUM': standard.rules.filter(r => r.severity === 'MEDIUM'),
        'LOW': standard.rules.filter(r => r.severity === 'LOW')
    };

    Object.entries(rulesBySeverity).forEach(([severity, rules]) => {
        if (rules.length === 0) return;

        const severityIcon = severity === 'HIGH' ? '🔴' : severity === 'MEDIUM' ? '🟡' : '🟢';
        const severityLabel = severity === 'HIGH' ? '高风险' : severity === 'MEDIUM' ? '中风险' : '低风险';

        html += `
            <div class="risk-section">
                <h5>${severityIcon} ${severityLabel} (${rules.length}条)</h5>
                <div class="rules-list">
        `;

        rules.forEach(rule => {
            html += `
                <div class="rule-item">
                    <div class="rule-header">
                        <strong>${rule.name}</strong>
                        <span class="rule-weight">权重: ${rule.weight}</span>
                    </div>
                    <div class="rule-description">${rule.description}</div>
                    <div class="rule-category">类别: ${rule.category}</div>
                    <div class="rule-keywords">关键词: ${rule.targetClauses.join(', ')}</div>
                </div>
            `;
        });

        html += `
                </div>
            </div>
        `;
    });

    html += '</div>';

    contentDiv.innerHTML = html;
    resultBox.style.display = 'block';
}

// ========== 智能审查Prompt生成功能 ==========

// 生成审查Prompt
async function generateReviewPrompt() {
    const contractType = document.getElementById('prompt-contract-type').value;
    const contractJson = document.getElementById('contract-json-input').value.trim();

    if (!contractJson) {
        showToast('请输入合同解析结果JSON', 'error');
        return;
    }

    // 验证JSON格式
    try {
        JSON.parse(contractJson);
    } catch (e) {
        showToast('合同JSON格式错误', 'error');
        return;
    }

    showLoading('prompt');
    hideResult('prompt');

    try {
        const response = await fetch('/standards/generate-prompt', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: contractJson
        });

        if (!response.ok) {
            throw new Error('生成Prompt失败');
        }

        const data = await response.json();
        showPromptResult(data);
        showToast('审查Prompt生成成功', 'success');
    } catch (error) {
        console.error('生成Prompt失败:', error);
        showToast('生成Prompt失败: ' + error.message, 'error');
    } finally {
        hideLoading('prompt');
    }
}

// 显示Prompt结果
function showPromptResult(data) {
    const resultBox = document.getElementById('prompt-result');
    const contentPre = document.getElementById('prompt-content');

    contentPre.textContent = data.prompt;
    resultBox.style.display = 'block';
}

// 复制Prompt结果
function copyPromptResult() {
    const promptContent = document.getElementById('prompt-content');
    const text = promptContent.textContent;

    navigator.clipboard.writeText(text).then(() => {
        showToast('Prompt已复制到剪贴板', 'success');
    }).catch(err => {
        console.error('复制失败:', err);
        showToast('复制失败', 'error');
    });
}

// ========== 扩展原有的switchTab函数 ==========

// 重新定义switchTab函数以支持新的选项卡
const originalSwitchTab = switchTab;
function switchTab(tabName) {
    // 移除所有活动状态
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    document.querySelectorAll('.panel').forEach(panel => {
        panel.classList.remove('active');
    });

    // 激活选中的选项卡 - 修复事件目标问题
    event.target.classList.add('active');
    document.getElementById(tabName + '-panel').classList.add('active');

    // 在切换到智能审查选项卡时，尝试从解析结果自动填充
    if (tabName === 'prompt') {
        const parseResultContent = document.getElementById('parse-result-content');
        const contractJsonInput = document.getElementById('contract-json-input');

        if (parseResultContent && parseResultContent.textContent && !contractJsonInput.value.trim()) {
            contractJsonInput.value = parseResultContent.textContent;
            showToast('已自动填充合同解析结果', 'success');
        }
    }

    // 切换到自动化审查选项卡时,检查AI服务状态
    if (tabName === 'auto-review') {
        checkAIServiceStatus();
    }
}

// ========== 自动化审查功能 ==========

// 处理自动化审查文件选择
function handleAutoReviewFileSelect(input) {
    const file = input.files[0];
    if (file) {
        autoReviewFile = file;
        const fileNameSpan = document.getElementById('auto-review-file-name');
        fileNameSpan.textContent = file.name;
        fileNameSpan.classList.add('selected');
    }
}

// 检查AI服务配置状态
async function checkAIServiceStatus() {
    try {
        const response = await fetch('/auto-review/status');
        const status = await response.json();

        displayAIServiceStatus(status);
    } catch (error) {
        console.error('检查AI服务状态失败:', error);
        document.getElementById('ai-status-content').innerHTML = `
            <p class="error-text">❌ 无法连接到后端服务</p>
        `;
    }
}

// 显示AI服务状态
function displayAIServiceStatus(status) {
    const contentDiv = document.getElementById('ai-status-content');

    const claudeStatus = status.claude.available ? '✅ 已配置' : '❌ 未配置';
    const openaiStatus = status.openai.available ? '✅ 已配置' : '❌ 未配置';
    const mockStatus = status.mock && status.mock.available ? '✅ 可用' : '❌ 不可用';
    const chatgptWebStatus = status.chatgptWeb && status.chatgptWeb.available ? '✅ 可用' : '❌ 不可用';

    let html = `
        <div class="status-grid">
            <div class="status-item">
                <strong>Claude:</strong> ${claudeStatus}
                ${status.claude.available ? `<span class="model-name">(${status.claude.model})</span>` : ''}
            </div>
            <div class="status-item">
                <strong>OpenAI:</strong> ${openaiStatus}
                ${status.openai.available ? `<span class="model-name">(${status.openai.model})</span>` : ''}
            </div>
            <div class="status-item">
                <strong>模拟AI:</strong> ${mockStatus}
                ${status.mock && status.mock.available ? `<span class="model-name">(测试用)</span>` : ''}
            </div>
            <div class="status-item">
                <strong>ChatGPT网页版:</strong> ${chatgptWebStatus}
                ${status.chatgptWeb && status.chatgptWeb.available ? `<span class="model-name">(https://chatgpt.com/)</span>` : ''}
            </div>
        </div>
    `;

    if (!status.autoReviewAvailable) {
        html += `
            <div class="warning-box">
                <p>⚠️ 未配置AI服务，请配置API密钥或使用模拟/ChatGPT网页版</p>
                <p class="config-hint">可选择: Claude API、OpenAI API、模拟服务或ChatGPT网页版</p>
            </div>
        `;
    } else {
        html += `
            <div class="success-box">
                <p>✅ AI服务已就绪，可以使用自动化审查功能</p>
                <p class="current-provider">当前配置: ${status.configuredProvider}</p>
            </div>
        `;
    }

    contentDiv.innerHTML = html;
}

// 开始自动化审查
async function startAutoReview() {
    if (!autoReviewFile) {
        showToast('请先选择合同文件', 'error');
        return;
    }

    const contractType = document.getElementById('auto-contract-type').value;
    const aiProvider = document.getElementById('ai-provider').value;
    const cleanupAnchors = document.getElementById('auto-cleanup-anchors').checked;

    // 显示进度和加载状态
    showAutoReviewProcess();
    showLoading('auto-review');
    hideResult('auto-review');

    // 构建FormData
    const formData = new FormData();
    formData.append('file', autoReviewFile);

    try {
        const url = `/auto-review?contractType=${contractType}&aiProvider=${aiProvider}&cleanupAnchors=${cleanupAnchors}`;

        // 更新进度 - 开始
        updateProcessStep(1, 'processing');

        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.message || '自动化审查失败');
        }

        // 所有步骤完成
        updateProcessStep(1, 'completed');
        updateProcessStep(2, 'completed');
        updateProcessStep(3, 'completed');
        updateProcessStep(4, 'completed');

        // 下载文件
        const blob = await response.blob();
        const filename = autoReviewFile.name.replace('.docx', '-AI审查完成.docx');
        downloadFile(blob, filename);

        // 显示结果
        showAutoReviewResult(filename);
        showToast('自动化审查完成! 文档已下载', 'success');

    } catch (error) {
        console.error('自动化审查失败:', error);
        showToast('自动化审查失败: ' + error.message, 'error');
        hideAutoReviewProcess();
    } finally {
        hideLoading('auto-review');
    }
}

// 显示自动化审查进度
function showAutoReviewProcess() {
    document.getElementById('auto-review-process').style.display = 'block';

    // 重置所有步骤
    for (let i = 1; i <= 4; i++) {
        updateProcessStep(i, 'pending');
    }
}

// 隐藏自动化审查进度
function hideAutoReviewProcess() {
    document.getElementById('auto-review-process').style.display = 'none';
}

// 更新进度步骤
function updateProcessStep(stepNumber, status) {
    const stepElement = document.getElementById(`step-${stepNumber}`);
    if (!stepElement) return;

    const iconSpan = stepElement.querySelector('.step-icon');

    // 移除旧状态
    stepElement.classList.remove('pending', 'processing', 'completed', 'error');

    // 添加新状态
    stepElement.classList.add(status);

    // 更新图标
    switch (status) {
        case 'pending':
            iconSpan.textContent = '⏳';
            break;
        case 'processing':
            iconSpan.textContent = '🔄';
            break;
        case 'completed':
            iconSpan.textContent = '✅';
            break;
        case 'error':
            iconSpan.textContent = '❌';
            break;
    }
}

// 显示自动化审查结果
function showAutoReviewResult(filename) {
    const resultBox = document.getElementById('auto-review-result');
    const summaryDiv = document.getElementById('auto-review-summary');

    summaryDiv.innerHTML = `
        <div class="review-summary">
            <p><strong>📄 文件名:</strong> ${filename}</p>
            <p><strong>✅ 状态:</strong> 审查成功</p>
            <p><strong>📊 流程:</strong> 解析 → AI审查 → 批注 → 输出</p>
        </div>
    `;

    resultBox.style.display = 'block';
}

// 重置自动化审查表单
function resetAutoReviewForm() {
    document.getElementById('auto-review-file').value = '';
    document.getElementById('auto-review-file-name').textContent = '仅支持 .docx 格式';
    document.getElementById('auto-review-file-name').classList.remove('selected');
    autoReviewFile = null;
    hideResult('auto-review');
    hideAutoReviewProcess();
}

// ========== ChatGPT 网页版集成功能 ==========

let chatgptFile = null;
let chatgptPrompt = null;

// 处理ChatGPT文件选择
function handleChatGPTFileSelect(input) {
    const file = input.files[0];
    if (file) {
        chatgptFile = file;
        const fileNameSpan = document.getElementById('chatgpt-file-name');
        fileNameSpan.textContent = file.name;
        fileNameSpan.classList.add('selected');

        // 清理之前的结果
        hideChatGPTPrompt();
        hideChatGPTImport();
    }
}

// 步骤1: 生成ChatGPT提示
async function generateChatGPTPrompt() {
    if (!chatgptFile) {
        showToast('请先选择合同文件', 'error');
        return;
    }

    const contractType = document.getElementById('chatgpt-contract-type').value;

    // 显示加载状态
    document.getElementById('chatgpt-generate-loading').style.display = 'block';
    hideChatGPTPrompt();

    // 构建FormData
    const formData = new FormData();
    formData.append('file', chatgptFile);

    try {
        const url = `/chatgpt/generate-prompt?contractType=${contractType}`;
        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || '生成提示失败');
        }

        const data = await response.json();
        showChatGPTPrompt(data);
        showToast('ChatGPT提示生成成功!', 'success');

    } catch (error) {
        console.error('生成ChatGPT提示失败:', error);
        showToast('生成提示失败: ' + error.message, 'error');
    } finally {
        document.getElementById('chatgpt-generate-loading').style.display = 'none';
    }
}

// 显示ChatGPT提示
function showChatGPTPrompt(data) {
    chatgptPrompt = data.chatgptPrompt;

    const promptBox = document.getElementById('chatgpt-prompt-result');
    const promptContent = document.getElementById('chatgpt-prompt-content');
    const instructionsList = document.getElementById('chatgpt-instructions');

    promptContent.textContent = data.chatgptPrompt;

    // 显示使用说明
    let instructionsHTML = '<h4>📋 使用步骤:</h4><ol>';
    data.instructions.forEach(instruction => {
        instructionsHTML += `<li>${instruction}</li>`;
    });
    instructionsHTML += '</ol>';
    instructionsList.innerHTML = instructionsHTML;

    promptBox.style.display = 'block';

    // 显示导入区域
    document.getElementById('chatgpt-import-section').style.display = 'block';
}

// 隐藏ChatGPT提示
function hideChatGPTPrompt() {
    document.getElementById('chatgpt-prompt-result').style.display = 'none';
    document.getElementById('chatgpt-import-section').style.display = 'none';
}

// 复制ChatGPT提示
function copyChatGPTPrompt() {
    if (!chatgptPrompt) {
        showToast('没有可复制的提示内容', 'error');
        return;
    }

    navigator.clipboard.writeText(chatgptPrompt).then(() => {
        showToast('ChatGPT提示已复制到剪贴板', 'success');

        // 提示用户下一步操作
        setTimeout(() => {
            showToast('请打开 https://chatgpt.com/ 并粘贴提示', 'warning');
        }, 1500);
    }).catch(err => {
        console.error('复制失败:', err);
        showToast('复制失败', 'error');
    });
}

// 打开ChatGPT网站
function openChatGPT() {
    window.open('https://chatgpt.com/', '_blank');
    showToast('ChatGPT网站已在新标签页打开', 'success');
}

// 步骤2: 导入ChatGPT审查结果
async function importChatGPTResult() {
    if (!chatgptFile) {
        showToast('请先选择合同文件', 'error');
        return;
    }

    const chatgptResponse = document.getElementById('chatgpt-response').value.trim();
    if (!chatgptResponse) {
        showToast('请输入ChatGPT的审查结果', 'error');
        return;
    }

    // 验证ChatGPT响应格式
    try {
        // 清理响应（移除markdown代码块）
        let cleanResponse = chatgptResponse.trim();
        if (cleanResponse.startsWith('```json')) {
            cleanResponse = cleanResponse.substring(7);
        }
        if (cleanResponse.startsWith('```')) {
            cleanResponse = cleanResponse.substring(3);
        }
        if (cleanResponse.endsWith('```')) {
            cleanResponse = cleanResponse.substring(0, cleanResponse.length - 3);
        }

        const parsed = JSON.parse(cleanResponse.trim());
        if (!parsed.issues) {
            throw new Error('ChatGPT响应缺少必需的issues字段');
        }
    } catch (e) {
        showToast('ChatGPT响应格式错误，请检查JSON格式', 'error');
        return;
    }

    const cleanupAnchors = document.getElementById('chatgpt-cleanup-anchors').checked;

    // 显示加载状态
    document.getElementById('chatgpt-import-loading').style.display = 'block';
    hideChatGPTImport();

    // 构建FormData
    const formData = new FormData();
    formData.append('file', chatgptFile);
    formData.append('chatgptResponse', chatgptResponse);

    try {
        const url = `/chatgpt/import-result?cleanupAnchors=${cleanupAnchors}`;
        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || '导入审查结果失败');
        }

        // 下载批注后的文件
        const blob = await response.blob();
        const filename = chatgptFile.name.replace('.docx', '_ChatGPT审查.docx');
        downloadFile(blob, filename);

        showChatGPTImportResult(filename);
        showToast('ChatGPT审查结果导入成功! 文档已下载', 'success');

    } catch (error) {
        console.error('导入ChatGPT审查结果失败:', error);
        showToast('导入失败: ' + error.message, 'error');
    } finally {
        document.getElementById('chatgpt-import-loading').style.display = 'none';
    }
}

// 显示ChatGPT导入结果
function showChatGPTImportResult(filename) {
    const resultBox = document.getElementById('chatgpt-import-result');
    const summaryDiv = document.getElementById('chatgpt-import-summary');

    summaryDiv.innerHTML = `
        <div class="import-summary">
            <p><strong>📄 文件名:</strong> ${filename}</p>
            <p><strong>✅ 状态:</strong> ChatGPT审查结果导入成功</p>
            <p><strong>📊 流程:</strong> 文件解析 → ChatGPT审查 → 结果导入 → 批注生成</p>
            <p><strong>💡 说明:</strong> 审查意见已添加到合同相应段落</p>
        </div>
    `;

    resultBox.style.display = 'block';
}

// 隐藏ChatGPT导入结果
function hideChatGPTImport() {
    document.getElementById('chatgpt-import-result').style.display = 'none';
}

// 重置ChatGPT表单
function resetChatGPTForm() {
    document.getElementById('chatgpt-file').value = '';
    document.getElementById('chatgpt-file-name').textContent = '仅支持 .docx 格式';
    document.getElementById('chatgpt-file-name').classList.remove('selected');
    document.getElementById('chatgpt-response').value = '';
    chatgptFile = null;
    chatgptPrompt = null;
    hideChatGPTPrompt();
    hideChatGPTImport();
}
