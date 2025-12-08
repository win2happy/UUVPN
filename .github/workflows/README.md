# GitHub Actions 工作流说明

本项目包含多个GitHub Actions工作流，用于自动化构建、测试和发布Android应用。

## 工作流概览

### 1. Android CI/CD (`android-ci.yml`)
- 在推送到main分支或创建PR时触发（仅当Android代码有变更时）
- 构建调试版本APK
- 运行单元测试
- 在推送到main分支时创建发布版本并上传到GitHub Releases

### 3. 代码质量检查 (`code-quality.yml`)
- 在推送到main分支或创建PR时触发
- 运行Kotlin代码的linter
- 执行安全检查
- 生成代码覆盖率报告

### 4. 发布工作流 (`release.yml`)
- 在推送标签时触发（格式为`v*`）
- 自动创建GitHub Release
- 构建并签名Android APK
- 将构建产物作为Release Assets上传

### 5. 依赖更新 (`dependency-update.yml`)
- 每周一自动运行
- 检查并更新Gradle依赖
- 自动创建Pull Request以合并依赖更新

## 配置要求

为了使这些工作流正常运行，您需要在仓库设置中配置以下secrets：

### Android Secrets
- `KEYSTORE`: Base64编码的签名密钥库文件
- `KEYSTORE_PASSWORD`: 密钥库密码
- `KEY_ALIAS`: 密钥别名
- `KEY_PASSWORD`: 密钥密码



## 使用方法

### 手动触发工作流
可以通过GitHub界面手动触发任何工作流：
1. 转到仓库的"Actions"选项卡
2. 选择要运行的工作流
3. 点击"Run workflow"按钮

### 自动触发
- **Push事件**: 推送到main分支会触发相关CI/CD工作流
- **Pull Request**: 创建PR会触发代码质量检查和构建验证
- **Tags**: 推送标签会触发发布工作流
- **Scheduled**: 依赖更新工作流每周一自动运行

## 自定义配置

您可以根据项目需求修改工作流文件：
- 修改触发条件（分支、路径等）
- 添加额外的测试步骤
- 更改构建参数
- 调整发布策略

所有工作流文件都位于`.github/workflows/`目录中。