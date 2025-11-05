# OnlyOffice Document Server 部署指南

## 方案一：Docker部署（推荐）

### 1. 使用Docker Compose部署

创建 `docker-compose-onlyoffice.yml` 文件：

```yaml
version: '3.8'
services:
  onlyoffice-documentserver:
    image: onlyoffice/documentserver:latest
    container_name: onlyoffice-documentserver
    ports:
      - "8080:80"
      - "8443:443"
    environment:
      - JWT_ENABLED=false
      - JWT_SECRET=your-secret-key
    volumes:
      - onlyoffice_data:/var/www/onlyoffice/Data
      - onlyoffice_logs:/var/log/onlyoffice
    restart: unless-stopped
    networks:
      - onlyoffice-network

volumes:
  onlyoffice_data:
  onlyoffice_logs:

networks:
  onlyoffice-network:
    driver: bridge
```

### 2. 启动服务

```bash
# 启动OnlyOffice Document Server
docker-compose -f docker-compose-onlyoffice.yml up -d

# 检查服务状态
docker-compose -f docker-compose-onlyoffice.yml ps

# 查看日志
docker-compose -f docker-compose-onlyoffice.yml logs -f
```

### 3. 验证部署

访问 `http://localhost:8080/healthcheck` 应该返回200状态码。

## 方案二：修改预览器配置（临时方案）

如果暂时不想部署OnlyOffice Document Server，可以修改预览器配置：

### 1. 修改OnlyOffice预览器配置

```javascript
// 在 onlyoffice-previewer.js 中修改
this.documentServerUrl = 'https://documentserver.onlyoffice.com'; // 使用在线服务
```

### 2. 或者禁用OnlyOffice检查

```javascript
// 修改初始化方法，跳过OnlyOffice检查
async init() {
    try {
        // 暂时跳过OnlyOffice检查
        this.isInitialized = true;
        console.log('OnlyOffice预览器初始化成功（跳过检查）');
        return true;
    } catch (error) {
        console.error('OnlyOffice预览器初始化失败:', error);
        this.isInitialized = false;
        return false;
    }
}
```

## 方案三：使用替代预览方案

### 1. PDF预览
对于PDF文件，可以使用浏览器原生预览：

```javascript
// 在 file-manager.js 中添加PDF预览
previewPDF(fileName, fileUrl) {
    window.open(fileUrl, '_blank');
}
```

### 2. 在线预览服务
使用Google Docs Viewer或其他在线预览服务：

```javascript
// Google Docs Viewer
const googleViewerUrl = `https://docs.google.com/gview?url=${encodeURIComponent(fileUrl)}&embedded=true`;
```

## 推荐步骤

1. **立即解决**：使用Docker部署OnlyOffice Document Server
2. **验证功能**：确保服务正常运行
3. **测试预览**：上传测试文件并验证预览功能

## 注意事项

- OnlyOffice Document Server需要较多资源（建议4GB+内存）
- 确保端口8080没有被其他服务占用
- 如果使用HTTPS，需要配置SSL证书
- 生产环境建议使用域名而非localhost
