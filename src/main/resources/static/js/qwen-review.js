// ========================================
// Qwen 一键审查功能 (qwen-review.js)
// ========================================

/**
 * 一键Qwen审查
 * 将规则审查生成的Prompt直接发送给Qwen，获取审查结果
 * 自动解析JSON并填充到导入框，用户可直接导入
 */
async function startQwenReview() {
    // 验证是否有Prompt
    const prompt = document.getElementById('rule-review-prompt').textContent;
    if (!prompt || prompt.trim() === '') {
        showToast('请先执行"开始规则审查"生成Prompt', 'error');
        return;
    }

    // 显示进度提示
    const progressDiv = document.getElementById('qwen-review-progress');
    const progressIcon = document.getElementById('qwen-progress-icon');
    const progressText = document.getElementById('qwen-progress-text');
    const qwenBtn = document.getElementById('qwen-review-btn');

    progressDiv.style.display = 'block';
    qwenBtn.disabled = true;
    qwenBtn.style.opacity = '0.6';

    progressIcon.textContent = '⏳';
    progressText.textContent = '正在调用Qwen进行审查...';

    try {
        console.log('准备调用Qwen审查接口...');
        console.log('Prompt长度:', prompt.length, '字符');

        // 构建请求
        const requestData = {
            prompt: prompt,
            contractType: document.getElementById('rule-review-contract-type').value,
            stance: document.querySelector('input[name="rule-review-stance"]:checked')?.value || 'Neutral'
        };

        console.log('请求数据:', {
            contractType: requestData.contractType,
            stance: requestData.stance,
            promptLength: requestData.prompt.length
        });

        // 调用Qwen审查接口
        const response = await fetch('/api/qwen/rule-review/review', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        });

        console.log('收到响应, 状态码:', response.status);

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || 'Qwen审查失败');
        }

        const result = await response.json();
        console.log('Qwen审查完成:', result);

        // 提取审查结果
        if (result.success && result.review) {
            // 自动填充到导入框
            const reviewJson = JSON.stringify(result.review, null, 2);
            document.getElementById('rule-review-response').value = reviewJson;

            // 【关键】确保 parseResultId 仍然可用
            console.log('✅ Qwen审查完成，当前 ruleReviewParseResultId:', window.ruleReviewParseResultId);

            const issueCount = result.issueCount || 0;
            progressIcon.textContent = '✅';
            progressText.textContent = '审查完成！检出 ' + issueCount + ' 个问题';

            // 显示成功提示
            setTimeout(() => {
                progressDiv.style.display = 'none';
                showToast('Qwen审查完成！检出 ' + issueCount + ' 个问题，已自动填充到导入框', 'success');

                // 提示用户下一步
                alert('Qwen审查完成！\n\n检出问题数: ' + issueCount + '\n处理时间: ' + result.processingTime + '\n\n下一步: 点击"导入并生成批注文档"按钮自动生成带批注的文档');

                // 自动滚动到导入部分
                document.getElementById('rule-review-import-section').scrollIntoView({ behavior: 'smooth' });
            }, 1000);

        } else {
            throw new Error('Qwen返回的数据格式不符合预期');
        }

    } catch (error) {
        console.error('Qwen审查失败:', error);
        progressIcon.textContent = '❌';
        progressText.textContent = '审查失败: ' + error.message;

        showToast('Qwen审查失败: ' + error.message, 'error');

        // 3秒后隐藏进度提示
        setTimeout(() => {
            progressDiv.style.display = 'none';
            qwenBtn.disabled = false;
            qwenBtn.style.opacity = '1';
        }, 3000);

    } finally {
        // 恢复按钮状态
        setTimeout(() => {
            qwenBtn.disabled = false;
            qwenBtn.style.opacity = '1';
        }, 2000);
    }
}

/**
 * 检查Qwen服务状态
 * 用于初始化时验证Qwen是否可用
 */
async function checkQwenStatus() {
    try {
        const response = await fetch('/api/qwen/rule-review/status');
        if (response.ok) {
            const data = await response.json();
            console.log('Qwen服务状态:', data);
            return data.qwenAvailable;
        }
    } catch (error) {
        console.warn('无法检查Qwen状态:', error.message);
    }
    return false;
}

// 页面加载时检查Qwen可用性
document.addEventListener('DOMContentLoaded', function() {
    // 异步检查Qwen服务
    setTimeout(() => {
        checkQwenStatus().then(available => {
            const btn = document.getElementById('qwen-review-btn');
            if (btn) {
                if (available) {
                    console.log('Qwen服务已就绪');
                    btn.title = '点击使用Qwen进行一键审查';
                } else {
                    console.warn('Qwen服务未配置');
                    btn.disabled = true;
                    btn.style.opacity = '0.5';
                    btn.title = 'Qwen服务未配置';
                }
            }
        });
    }, 500);
});
