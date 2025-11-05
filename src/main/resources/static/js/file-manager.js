/**
 * 文件管理器组件
 * 提供MinIO云桶文件列表和预览功能
 */

class FileManager {
    constructor() {
        this.files = [];
        this.currentFile = null;
        this.isLoading = false;
    }

    /**
     * 初始化文件管理器
     */
    async init() {
        try {
            await this.loadFileList();
            this.renderFileList();
            return true;
        } catch (error) {
            console.error('文件管理器初始化失败:', error);
            this.showError('文件管理器初始化失败: ' + error.message);
            return false;
        }
    }

    /**
     * 加载文件列表
     */
    async loadFileList() {
        this.isLoading = true;
        this.showLoading('正在加载文件列表...');

        try {
            const response = await fetch('/api/preview/files');
            const result = await response.json();

            if (result.success) {
                this.files = result.files || [];
                console.log(`加载文件列表成功，共${this.files.length}个文件`);
            } else {
                throw new Error(result.error || '获取文件列表失败');
            }
        } catch (error) {
            console.error('加载文件列表失败:', error);
            throw error;
        } finally {
            this.isLoading = false;
        }
    }

    /**
     * 渲染文件列表
     */
    renderFileList() {
        const container = document.getElementById('file-list-container');
        if (!container) {
            console.error('文件列表容器不存在');
            return;
        }

        if (this.files.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">📁</div>
                    <div class="empty-text">暂无文件</div>
                    <div class="empty-desc">上传文件后即可在此查看和预览</div>
                </div>
            `;
            return;
        }

        const fileListHtml = this.files.map(file => this.createFileItem(file)).join('');
        container.innerHTML = `
            <div class="file-list">
                <div class="file-list-header">
                    <div class="file-count">共 ${this.files.length} 个文件</div>
                    <button class="btn-refresh" onclick="fileManager.refresh()">
                        <span>🔄</span> 刷新
                    </button>
                </div>
                <div class="file-items">
                    ${fileListHtml}
                </div>
            </div>
        `;
    }

    /**
     * 创建文件项
     */
    createFileItem(file) {
        const fileName = file.name;
        const fileSize = this.formatFileSize(file.size);
        const lastModified = this.formatDate(file.lastModified);
        const fileIcon = this.getFileIcon(fileName);
        const isSupported = this.isFileSupported(fileName);

        return `
            <div class="file-item ${isSupported ? 'supported' : 'unsupported'}" 
                 data-file-name="${fileName}" 
                 data-file-url="${file.url}">
                <div class="file-icon">${fileIcon}</div>
                <div class="file-info">
                    <div class="file-name" title="${fileName}">${fileName}</div>
                    <div class="file-meta">
                        <span class="file-size">${fileSize}</span>
                        <span class="file-date">${lastModified}</span>
                    </div>
                </div>
                <div class="file-actions">
                    ${isSupported ? `
                        <button class="btn-preview" onclick="fileManager.previewFile('${fileName}', '${file.url}')">
                            <span>👁️</span> 预览
                        </button>
                    ` : `
                        <button class="btn-download" onclick="fileManager.downloadFile('${fileName}', '${file.url}')">
                            <span>⬇️</span> 下载
                        </button>
                    `}
                </div>
            </div>
        `;
    }

    /**
     * 预览文件
     */
    async previewFile(fileName, fileUrl) {
        try {
            this.currentFile = { name: fileName, url: fileUrl };
            
            // 显示预览面板
            this.showPreviewPanel();
            
            // 使用OnlyOffice预览器预览文件
            const success = await window.onlyOfficePreviewer.previewFile(fileName, fileUrl);
            
            if (success) {
                this.updatePreviewHeader(fileName);
            } else {
                this.showError('文件预览失败');
            }
        } catch (error) {
            console.error('预览文件失败:', error);
            this.showError('预览文件失败: ' + error.message);
        }
    }

    /**
     * 下载文件
     */
    downloadFile(fileName, fileUrl) {
        try {
            const link = document.createElement('a');
            link.href = fileUrl;
            link.download = fileName;
            link.target = '_blank';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } catch (error) {
            console.error('下载文件失败:', error);
            this.showError('下载文件失败: ' + error.message);
        }
    }

    /**
     * 刷新文件列表
     */
    async refresh() {
        try {
            await this.loadFileList();
            this.renderFileList();
            this.showSuccess('文件列表已刷新');
        } catch (error) {
            console.error('刷新文件列表失败:', error);
            this.showError('刷新文件列表失败: ' + error.message);
        }
    }

    /**
     * 显示预览面板
     */
    showPreviewPanel() {
        const previewPanel = document.getElementById('preview-panel');
        if (previewPanel) {
            previewPanel.style.display = 'block';
        }
    }

    /**
     * 更新预览头部
     */
    updatePreviewHeader(fileName) {
        const header = document.getElementById('preview-header');
        if (header) {
            header.innerHTML = `
                <div class="preview-title">
                    <span class="preview-icon">📄</span>
                    <span class="preview-name">${fileName}</span>
                </div>
                <div class="preview-actions">
                    <button class="btn-close" onclick="fileManager.closePreview()">
                        <span>✕</span> 关闭
                    </button>
                </div>
            `;
        }
    }

    /**
     * 关闭预览
     */
    closePreview() {
        window.onlyOfficePreviewer.destroy();
        const previewPanel = document.getElementById('preview-panel');
        if (previewPanel) {
            previewPanel.style.display = 'none';
        }
        this.currentFile = null;
    }

    /**
     * 检查文件是否支持预览
     */
    isFileSupported(fileName) {
        const extension = this.getFileExtension(fileName).toLowerCase();
        const supportedFormats = [
            'docx', 'doc', 'xlsx', 'xls', 'pptx', 'ppt', 
            'pdf', 'txt', 'rtf', 'odt', 'ods', 'odp'
        ];
        return supportedFormats.includes(extension);
    }

    /**
     * 获取文件扩展名
     */
    getFileExtension(fileName) {
        if (!fileName || fileName.lastIndexOf('.') === -1) {
            return '';
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    /**
     * 获取文件图标
     */
    getFileIcon(fileName) {
        const extension = this.getFileExtension(fileName).toLowerCase();
        
        const iconMap = {
            'docx': '📄', 'doc': '📄',
            'xlsx': '📊', 'xls': '📊',
            'pptx': '📽️', 'ppt': '📽️',
            'pdf': '📕',
            'txt': '📝',
            'rtf': '📄',
            'odt': '📄', 'ods': '📊', 'odp': '📽️'
        };
        
        return iconMap[extension] || '📄';
    }

    /**
     * 格式化文件大小
     */
    formatFileSize(bytes) {
        if (bytes === 0) return '0 B';
        
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    /**
     * 格式化日期
     */
    formatDate(dateString) {
        try {
            const date = new Date(dateString);
            return date.toLocaleString('zh-CN', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (error) {
            return '未知时间';
        }
    }

    /**
     * 显示加载状态
     */
    showLoading(message) {
        const container = document.getElementById('file-list-container');
        if (container) {
            container.innerHTML = `
                <div class="loading-state">
                    <div class="loading-spinner"></div>
                    <div class="loading-text">${message}</div>
                </div>
            `;
        }
    }

    /**
     * 显示错误信息
     */
    showError(message) {
        this.showMessage(message, 'error');
    }

    /**
     * 显示成功信息
     */
    showSuccess(message) {
        this.showMessage(message, 'success');
    }

    /**
     * 显示消息
     */
    showMessage(message, type = 'info') {
        // 使用现有的toast系统
        if (window.showToast) {
            window.showToast(message, type);
        } else {
            console.log(`[${type.toUpperCase()}] ${message}`);
        }
    }
}

// 全局实例
window.fileManager = new FileManager();
