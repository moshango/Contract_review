// 全局变量
let parseFile = null;
let annotateFile = null;

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
}
