/**
 * OnlyOffice预览器组件
 * 提供文件预览功能，支持多种文档格式
 */

class OnlyOfficePreviewer {
    constructor() {
        this.documentServerUrl = 'http://127.0.0.1:8082'; // OnlyOffice Document Server地址
        this.isInitialized = false;
        this.currentDocument = null;
        // 健康检查结果缓存（毫秒）
        this.healthcheckCache = { timestamp: 0, ok: false };
        this.healthcheckTtlMs = 60 * 1000; // 60秒
    }

    /**
     * 初始化OnlyOffice预览器
     */
    async init() {
        try {
            // 检查OnlyOffice Document Server是否可用（带缓存）
            const isAvailable = await this.checkDocumentServer();
            if (!isAvailable) {
                console.warn('OnlyOffice Document Server不可用，将使用备用预览方案');
                // 不抛出错误，允许使用备用方案
            }

            this.isInitialized = true;
            console.log('OnlyOffice预览器初始化成功');
            return true;
        } catch (error) {
            console.error('OnlyOffice预览器初始化失败:', error);
            this.isInitialized = false;
            return false;
        }
    }

    /**
     * 检查OnlyOffice Document Server是否可用（带缓存）
     */
    async checkDocumentServer() {
        try {
            const now = Date.now();
            if (now - this.healthcheckCache.timestamp < this.healthcheckTtlMs) {
                return this.healthcheckCache.ok;
            }
            
            // 简化检查：直接尝试加载DocsAPI脚本
            if (window.DocsAPI && window.DocsAPI.DocEditor) {
                this.healthcheckCache = { timestamp: now, ok: true };
                return true;
            }
            
            // 如果DocsAPI未加载，尝试动态加载
            try {
                await this.loadDocsAPI();
                if (window.DocsAPI && window.DocsAPI.DocEditor) {
                    this.healthcheckCache = { timestamp: now, ok: true };
                    return true;
                }
            } catch (e) {
                console.warn('动态加载DocsAPI失败:', e);
            }
            
            this.healthcheckCache = { timestamp: now, ok: false };
            return false;
        } catch (error) {
            console.warn('OnlyOffice Document Server检查失败:', error);
            this.healthcheckCache = { timestamp: Date.now(), ok: false };
            return false;
        }
    }

    /**
     * 动态加载DocsAPI脚本
     */
    async loadDocsAPI() {
        return new Promise((resolve, reject) => {
            if (window.DocsAPI) {
                resolve();
                return;
            }
            
            const script = document.createElement('script');
            script.src = `${this.documentServerUrl}/web-apps/apps/api/documents/api.js`;
            script.onload = () => resolve();
            script.onerror = () => reject(new Error('Failed to load DocsAPI'));
            document.head.appendChild(script);
        });
    }

    /**
     * 预览文件
     * @param {string} fileName 文件名
     * @param {string} fileUrl 文件URL
     * @param {string} containerId 容器ID
     */
    async previewFile(fileName, fileUrl, containerId = 'onlyoffice-container') {
        if (!this.isInitialized) {
            const initialized = await this.init();
            if (!initialized) {
                throw new Error('OnlyOffice预览器未初始化');
            }
        }

        try {
            // 检查文件是否支持预览
            const supported = await this.checkFileSupport(fileName);
            if (!supported) {
                throw new Error(`文件格式不支持预览: ${fileName}`);
            }

            // 创建预览容器
            this.createPreviewContainer(containerId);

            // 检查OnlyOffice Document Server是否可用（带缓存）
            const isOnlyOfficeAvailable = await this.checkDocumentServer();
            
            if (isOnlyOfficeAvailable && window.DocsAPI) {
                // 使用后端代理URL（查询参数形式，避免编码斜杠问题），并使用容器可达主机名
                const proxyUrl = `http://127.0.0.1:8080/api/preview/proxy?fileName=${encodeURIComponent(fileName)}`;

                // 从后端获取签名的EditorConfig（后端会根据配置附加JWT）
                const resp = await fetch(`/api/preview/onlyoffice/editor-config?fileName=${encodeURIComponent(fileName)}&fileUrl=${encodeURIComponent(proxyUrl)}&mode=view`);
                const result = await resp.json();
                if (!result || !result.success) {
                    throw new Error((result && result.error) ? result.error : '获取EditorConfig失败');
                }
                this.currentDocument = new DocsAPI.DocEditor(containerId, result.config);
                console.log(`使用OnlyOffice预览文件: ${fileName}`);
            } else {
                // 使用备用预览方案
                this.useFallbackPreview(fileName, fileUrl, containerId);
                console.log(`使用备用方案预览文件: ${fileName}`);
            }
            
            return true;

        } catch (error) {
            console.error('文件预览失败:', error);
            this.showError(`预览失败: ${error.message}`);
            return false;
        }
    }

    /**
     * 检查文件是否支持预览
     */
    async checkFileSupport(fileName) {
        try {
            // 直接在客户端检查文件扩展名，避免API调用问题
            const extension = this.getFileExtension(fileName).toLowerCase();
            const supportedFormats = [
                'docx', 'doc', 'xlsx', 'xls', 'pptx', 'ppt', 
                'pdf', 'txt', 'rtf', 'odt', 'ods', 'odp'
            ];
            
            const supported = supportedFormats.includes(extension);
            console.log(`文件支持检查: ${fileName} -> ${extension} -> ${supported ? '支持' : '不支持'}`);
            return supported;
        } catch (error) {
            console.error('检查文件支持状态失败:', error);
            return false;
        }
    }

    /**
     * 创建OnlyOffice文档配置
     */
    createDocumentConfig(fileName, fileUrl) {
        const fileExtension = this.getFileExtension(fileName).toLowerCase();
        
        return {
            "document": {
                "fileType": fileExtension,
                "key": this.generateDocumentKey(fileName),
                "title": fileName,
                "url": fileUrl
            },
            "documentType": this.getDocumentType(fileExtension),
            "editorConfig": {
                "mode": "view", // 只读模式
                "lang": "zh",
                "region": "zh-CN", // 使用region替代已废弃的location
                "user": {
                    "id": "user_" + Date.now(),
                    "name": "预览用户"
                },
                "customization": {
                    "autosave": false,
                    "forcesave": false,
                    "chat": false,
                    "comments": false,
                    "help": true,
                    "hideRightMenu": true,
                    "hideRulers": false,
                    "compactHeader": true,
                    "compactToolbar": true,
                    "toolbarNoTabs": true,
                    "zoom": 100,
                    "macros": false,
                    "macrosMode": "disabled",
                    "plugins": false,
                    "spellcheck": false,
                    "unit": "cm"
                }
            },
            "height": "100%",
            "width": "100%",
            "events": {
                "onDocumentReady": () => {
                    console.log('文档加载完成');
                },
                "onDocumentStateChange": (event) => {
                    console.log('文档状态变化:', event);
                },
                "onError": (event) => {
                    console.error('OnlyOffice错误:', event);
                    this.showError(`预览错误: ${event.data}`);
                }
            }
        };
    }

    /**
     * 创建预览容器
     */
    createPreviewContainer(containerId) {
        // 移除现有容器
        const existingContainer = document.getElementById(containerId);
        if (existingContainer) {
            existingContainer.remove();
        }

        // 创建新容器
        const container = document.createElement('div');
        container.id = containerId;
        container.style.cssText = `
            width: 100%;
            height: 600px;
            border: 1px solid #ddd;
            border-radius: 8px;
            background: #f8f9fa;
            position: relative;
        `;

        // 添加加载提示
        const loadingDiv = document.createElement('div');
        loadingDiv.innerHTML = `
            <div style="
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                text-align: center;
                color: #666;
            ">
                <div style="font-size: 24px; margin-bottom: 10px;">📄</div>
                <div>正在加载文档预览...</div>
            </div>
        `;
        container.appendChild(loadingDiv);

        // 插入到页面
        const targetElement = document.getElementById('preview-panel') || document.body;
        targetElement.appendChild(container);

        return container;
    }

    /**
     * 使用备用预览方案
     */
    useFallbackPreview(fileName, fileUrl, containerId) {
        const container = document.getElementById(containerId);
        if (!container) return;

        const fileExtension = this.getFileExtension(fileName).toLowerCase();
        
        if (fileExtension === 'pdf') {
            // PDF文件使用iframe预览
            container.innerHTML = `
                <iframe src="${fileUrl}" 
                        style="width: 100%; height: 100%; border: none;"
                        title="PDF预览">
                </iframe>
            `;
        } else if (['docx', 'doc', 'xlsx', 'xls', 'pptx', 'ppt'].includes(fileExtension)) {
            // Office文档使用 Office Web Viewer（避免 Google gview 401）
            const officeWebViewerUrl = `https://view.officeapps.live.com/op/view.aspx?src=${encodeURIComponent(fileUrl)}`;
            container.innerHTML = `
                <iframe src="${officeWebViewerUrl}" 
                        style="width: 100%; height: 100%; border: none;"
                        title="文档预览">
                </iframe>
                <div style="position: absolute; top: 10px; right: 10px; background: rgba(0,0,0,0.7); color: white; padding: 5px 10px; border-radius: 4px; font-size: 12px;">
                    使用 Office Web Viewer 预览
                </div>
            `;
        } else if (fileExtension === 'txt') {
            // 文本文件直接显示内容
            this.previewTextFile(fileUrl, container);
        } else {
            // 其他格式显示下载链接
            container.innerHTML = `
                <div style="
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    text-align: center;
                    padding: 20px;
                ">
                    <div style="font-size: 48px; margin-bottom: 20px;">📄</div>
                    <h3 style="margin-bottom: 15px;">${fileName}</h3>
                    <p style="margin-bottom: 20px; color: #666;">此文件格式暂不支持在线预览</p>
                    <a href="${fileUrl}" 
                       download="${fileName}" 
                       style="
                           display: inline-block;
                           background: #3498db;
                           color: white;
                           padding: 10px 20px;
                           text-decoration: none;
                           border-radius: 5px;
                           font-weight: 500;
                       ">
                        📥 下载文件
                    </a>
                </div>
            `;
        }
    }

    /**
     * 预览文本文件
     */
    async previewTextFile(fileUrl, container) {
        try {
            const response = await fetch(fileUrl);
            const text = await response.text();
            
            container.innerHTML = `
                <div style="
                    padding: 20px;
                    height: 100%;
                    overflow-y: auto;
                    font-family: 'Courier New', monospace;
                    font-size: 14px;
                    line-height: 1.5;
                    background: #f8f9fa;
                    white-space: pre-wrap;
                    word-wrap: break-word;
                ">
                    ${text}
                </div>
            `;
        } catch (error) {
            container.innerHTML = `
                <div style="
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    text-align: center;
                    color: #e74c3c;
                ">
                    <div style="font-size: 24px; margin-bottom: 10px;">❌</div>
                    <div>无法加载文本文件</div>
                </div>
            `;
        }
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
     * 获取文档类型
     */
    getDocumentType(extension) {
        const wordTypes = ['docx', 'doc', 'odt', 'rtf'];
        const excelTypes = ['xlsx', 'xls', 'ods'];
        const powerpointTypes = ['pptx', 'ppt', 'odp'];
        
        if (wordTypes.includes(extension)) return 'word';
        if (excelTypes.includes(extension)) return 'cell';
        if (powerpointTypes.includes(extension)) return 'slide';
        return 'word'; // 默认
    }

    /**
     * 生成文档密钥
     */
    generateDocumentKey(fileName) {
        return 'key_' + fileName + '_' + Date.now();
    }

    /**
     * 显示错误信息
     */
    showError(message) {
        const container = document.getElementById('onlyoffice-container');
        if (container) {
            container.innerHTML = `
                <div style="
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    text-align: center;
                    color: #e74c3c;
                    padding: 20px;
                ">
                    <div style="font-size: 24px; margin-bottom: 10px;">❌</div>
                    <div>${message}</div>
                </div>
            `;
        }
    }

    /**
     * 销毁预览器
     */
    destroy() {
        if (this.currentDocument) {
            this.currentDocument.destroyEditor();
            this.currentDocument = null;
        }
        
        const container = document.getElementById('onlyoffice-container');
        if (container) {
            container.remove();
        }
    }
}

// 全局实例
window.onlyOfficePreviewer = new OnlyOfficePreviewer();