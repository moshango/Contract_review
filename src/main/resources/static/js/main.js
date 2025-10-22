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
        // 当选择 both 模式时，先获取 JSON，再获取文件
        if (returnMode === 'both') {
            // 第一步: 获取 JSON 结果
            const jsonUrl = `/api/parse?anchors=${anchorMode}&returnMode=json`;
            const jsonResponse = await fetch(jsonUrl, {
                method: 'POST',
                body: formData
            });

            if (!jsonResponse.ok) {
                const errorData = await jsonResponse.json();
                throw new Error(errorData.error || '解析失败');
            }

            const data = await jsonResponse.json();
            showParseResult(data);
            showToast('JSON结果已加载', 'success');

            // 第二步: 获取带锚点的文件
            const fileUrl = `/api/parse?anchors=${anchorMode}&returnMode=file`;
            // 需要重新创建FormData，因为第一次请求已经消耗了
            const formData2 = new FormData();
            formData2.append('file', parseFile);

            const fileResponse = await fetch(fileUrl, {
                method: 'POST',
                body: formData2
            });

            if (fileResponse.ok) {
                const blob = await fileResponse.blob();
                const filename = `parsed-${parseFile.name}`;
                downloadFile(blob, filename);
                showToast('文档已下载', 'success');
            } else {
                console.warn('文件下载失败，但JSON已成功加载');
            }
        } else {
            // JSON 或 FILE 模式
            const url = `/api/parse?anchors=${anchorMode}&returnMode=${returnMode}`;
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
            } else if (returnMode === 'file') {
                const blob = await response.blob();
                const filename = `parsed-${parseFile.name}`;
                downloadFile(blob, filename);
                showToast('解析成功! 文档已下载', 'success');
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
        const url = `/api/annotate?anchorStrategy=${anchorStrategy}&cleanupAnchors=${cleanupAnchors}`;
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
        const response = await fetch('/api/health');
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
    const doubaoStatus = status.doubao && status.doubao.available ? '✅ 已配置' : '❌ 未配置';
    const mockStatus = status.mock && status.mock.available ? '✅ 可用' : '❌ 不可用';
    const chatgptWebStatus = status.chatgptWeb && status.chatgptWeb.available ? '✅ 可用' : '❌ 不可用';

    let html = `
        <div class="status-grid">
            <div class="status-item doubao">
                <strong>豆包 (火山引擎):</strong> ${doubaoStatus}
                ${status.doubao && status.doubao.available ? `<span class="model-name">(${status.doubao.model})</span>` : ''}
                ${status.doubao && status.doubao.description ? `<div class="service-desc">${status.doubao.description}</div>` : ''}
            </div>
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
                <p class="config-hint">可选择: 豆包API、Claude API、OpenAI API、模拟服务或ChatGPT网页版</p>
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
let chatgptParseResultId = null;  // 【关键修复】存储parseResultId用于后续批注

// 【调试函数】更新调试面板
function updateChatGPTDebugPanel() {
    const debugSpan = document.getElementById('debug-parseResultId');
    const statusSpan = document.getElementById('debug-status');

    if (chatgptParseResultId) {
        debugSpan.textContent = chatgptParseResultId;
        debugSpan.style.color = '#28a745';
        statusSpan.textContent = '✅ parseResultId 已保存，可以进行步骤2';
        statusSpan.style.color = '#28a745';
    } else {
        debugSpan.textContent = '未设置';
        debugSpan.style.color = '#ff6b6b';
        statusSpan.textContent = '❌ parseResultId 未设置，请先执行步骤1';
        statusSpan.style.color = '#ff6b6b';
    }
}

// 【调试函数】显示全部调试信息
function debugShowParseResultId() {
    const info = `
=== ChatGPT 集成调试信息 ===
parseResultId: ${chatgptParseResultId || '未设置'}
chatgptFile: ${chatgptFile ? chatgptFile.name : '未选择'}
chatgptPrompt: ${chatgptPrompt ? '已保存 (' + chatgptPrompt.length + ' 字符)' : '未生成'}
当前时间: ${new Date().toLocaleString('zh-CN')}

【关键检查】
1. 如果 parseResultId 为"未设置"，说明步骤1 还未执行或执行失败
2. 如果 parseResultId 有值，说明已正确保存
3. 如果为空，检查浏览器控制台是否有错误信息

【网络检查】
打开浏览器 F12 → Network 标签
- 找到 /chatgpt/generate-prompt 请求
- 检查响应 JSON 中是否包含 "parseResultId" 字段
    `;

    console.log(info);
    alert('调试信息已输出到控制台 (F12 → Console)，以下是摘要：\n\n' +
          'parseResultId: ' + (chatgptParseResultId || '未设置') + '\n' +
          'chatgptFile: ' + (chatgptFile ? chatgptFile.name : '未选择') + '\n\n' +
          '请检查浏览器控制台获取更多信息');
}

// 【调试函数】清除缓存和状态
function clearChatGPTDebug() {
    chatgptFile = null;
    chatgptPrompt = null;
    chatgptParseResultId = null;
    console.log('✅ 已清除所有缓存');
    updateChatGPTDebugPanel();
    showToast('已清除缓存，请重新开始', 'info');
}

// 处理ChatGPT文件选择
function handleChatGPTFileSelect(input) {
    const file = input.files[0];
    if (file) {
        chatgptFile = file;
        chatgptParseResultId = null;  // 重置parseResultId
        const fileNameSpan = document.getElementById('chatgpt-file-name');
        fileNameSpan.textContent = file.name;
        fileNameSpan.classList.add('selected');

        console.log('📁 选择文件:', file.name);
        updateChatGPTDebugPanel();

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

    console.log('🚀 开始生成ChatGPT提示...');
    console.log('   文件:', chatgptFile.name);
    console.log('   合同类型:', contractType);

    // 显示加载状态
    document.getElementById('chatgpt-generate-loading').style.display = 'block';
    hideChatGPTPrompt();

    // 构建FormData
    const formData = new FormData();
    formData.append('file', chatgptFile);

    try {
        // 【关键修复】使用generate锚点模式，确保返回parseResultId
        const url = `/chatgpt/generate-prompt?contractType=${contractType}&anchors=generate`;
        console.log('📡 请求URL:', url);

        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });

        console.log('📥 收到响应，状态码:', response.status);

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || '生成提示失败');
        }

        const data = await response.json();

        console.log('✅ 响应数据:', data);
        console.log('   - parseResultId:', data.parseResultId);
        console.log('   - chatgptPrompt 长度:', data.chatgptPrompt ? data.chatgptPrompt.length : 0);
        console.log('   - anchorsEnabled:', data.anchorsEnabled);

        // 【关键修复】保存parseResultId到全局变量
        if (data.parseResultId) {
            chatgptParseResultId = data.parseResultId;
            console.log('✅ 【关键】已保存parseResultId到全局变量:', chatgptParseResultId);
            console.log('   使用 window.chatgptParseResultId 可在控制台查看');
            showToast('✅ 已生成锚点，parseResultId已保存用于后续批注', 'success');
            updateChatGPTDebugPanel();
        } else {
            console.warn('⚠️ 响应中未包含parseResultId，后续批注可能不精确');
            console.warn('   检查后端是否已修改 /generate-prompt 端点');
            showToast('⚠️ 警告：未获取到parseResultId，批注定位精度可能降低', 'warning');
        }

        showChatGPTPrompt(data);
        showToast('ChatGPT提示生成成功!', 'success');

    } catch (error) {
        console.error('❌ 生成ChatGPT提示失败:', error);
        console.error('   错误信息:', error.message);
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
    let parsedResponse = null;
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

        parsedResponse = JSON.parse(cleanResponse.trim());
        if (!parsedResponse.issues) {
            throw new Error('ChatGPT响应缺少必需的issues字段');
        }

        // 【新增】验证targetText字段
        const preciseAnnotationStats = analyzePreciseAnnotationSupport(parsedResponse.issues);
        if (preciseAnnotationStats.total > 0) {
            const precisePercentage = ((preciseAnnotationStats.withTargetText / preciseAnnotationStats.total) * 100).toFixed(1);
            console.info(`精确文字批注支持: ${preciseAnnotationStats.withTargetText}/${preciseAnnotationStats.total} 条问题 (${precisePercentage}%)`);
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
    // 【关键修复】只有当成功获取parseResultId时才传递file，否则两个都传递
    if (!chatgptParseResultId) {
        // 降级方案：传递file（此时会使用不带锚点的文档）
        formData.append('file', chatgptFile);
        console.warn('⚠️ parseResultId不存在，将使用原始文件进行批注（可能定位不精确）');
    }
    formData.append('chatgptResponse', chatgptResponse);

    try {
        // 【关键修复】构建URL并传递parseResultId参数
        let url = `/chatgpt/import-result?cleanupAnchors=${cleanupAnchors}`;

        console.log('🚀 开始导入ChatGPT审查结果...');
        console.log('   cleanupAnchors:', cleanupAnchors);
        console.log('   chatgptParseResultId:', chatgptParseResultId);

        if (chatgptParseResultId) {
            url += `&parseResultId=${encodeURIComponent(chatgptParseResultId)}`;
            console.log('✅ 【关键】将传递parseResultId参数');
            console.log('📡 请求URL:', url);
            showToast('✅ 使用缓存的带锚点文档进行批注...', 'info');
        } else {
            console.warn('⚠️ parseResultId 不存在，将使用原始文件进行批注（可能定位不精确）');
            console.warn('   当前 chatgptParseResultId:', chatgptParseResultId);
            console.warn('   如果此值为 null，说明步骤1 可能没有正确执行');
            showToast('⚠️ 警告：batch注定位可能不精确', 'warning');
        }

        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });

        console.log('📥 收到响应，状态码:', response.status);

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || '导入审查结果失败');
        }

        // 下载批注后的文件
        const blob = await response.blob();
        const filename = chatgptFile.name.replace('.docx', '_ChatGPT审查.docx');
        downloadFile(blob, filename);

        console.log('✅ 文件下载成功:', filename);
        showChatGPTImportResult(filename, parsedResponse.issues.length, !!chatgptParseResultId);
        showToast('✅ ChatGPT审查结果导入成功! 文档已下载', 'success');

    } catch (error) {
        console.error('❌ 导入ChatGPT审查结果失败:', error);
        console.error('   错误信息:', error.message);
        console.error('   当前 parseResultId:', chatgptParseResultId);
        showToast('导入失败: ' + error.message, 'error');
    } finally {
        document.getElementById('chatgpt-import-loading').style.display = 'none';
    }
}

/**
 * 【新增】分析ChatGPT响应中的精确文字批注支持情况
 * 用于统计有多少问题包含了targetText字段
 */
function analyzePreciseAnnotationSupport(issues) {
    if (!issues || !Array.isArray(issues)) {
        return { total: 0, withTargetText: 0, missingMatchPattern: 0 };
    }

    let total = issues.length;
    let withTargetText = 0;
    let missingMatchPattern = 0;

    issues.forEach(issue => {
        if (issue.targetText && issue.targetText.trim() !== '') {
            withTargetText++;
            if (!issue.matchPattern || issue.matchPattern.trim() === '') {
                missingMatchPattern++;
            }
        }
    });

    return { total, withTargetText, missingMatchPattern };
}

// 显示ChatGPT导入结果
function showChatGPTImportResult(filename, issuesCount, usedAnchors = false) {
    const resultBox = document.getElementById('chatgpt-import-result');
    const summaryDiv = document.getElementById('chatgpt-import-summary');

    // 【关键修复】根据是否使用了锚点显示不同的提示
    const anchorStatusHTML = usedAnchors
        ? '<p style="color: #28a745; font-weight: bold;">✅ 使用缓存的带锚点文档进行批注 - 定位精度最高</p>'
        : '<p style="color: #ffc107; font-weight: bold;">⚠️ 使用原始文件进行批注 - 定位精度可能降低</p>';

    summaryDiv.innerHTML = `
        <div class="import-summary">
            <p><strong>📄 文件名:</strong> ${filename}</p>
            ${anchorStatusHTML}
            <p><strong>📊 问题数量:</strong> ${issuesCount || '?'} 条问题已批注</p>
            <p><strong>📑 流程:</strong> 文件解析 → ChatGPT审查 → 结果导入 → 批注生成</p>
            <p><strong>💡 说明:</strong> 审查意见已添加到合同相应位置（支持精确文字级别批注）</p>
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
    chatgptParseResultId = null;  // 【关键修复】重置parseResultId
    hideChatGPTPrompt();
    hideChatGPTImport();
}
