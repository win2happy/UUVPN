# GitHub Actions 实现说明

本文档说明了为UUVPN项目实现的GitHub Actions工作流。

## 实现的工作流

### 1. Android CI/CD
文件：`.github/workflows/android-ci.yml`

功能：
- 在推送到main分支时自动构建Android应用
- 运行单元测试
- 创建发布版本并签名APK
- 上传到GitHub Releases

### 3. 代码质量检查
文件：`.github/workflows/code-quality.yml`

功能：
- 运行Kotlin代码的linter
- 执行安全检查
- 生成代码覆盖率报告

### 4. 发布工作流
文件：`.github/workflows/release.yml`

功能：
- 在推送标签时自动创建GitHub Release
- 构建并签名Android应用
- 将构建产物作为Release Assets上传

### 5. 依赖更新
文件：`.github/workflows/dependency-update.yml`

功能：
- 每周一自动检查依赖更新
- 创建Pull Request以合并更新

## 配置要求

为了使这些工作流正常运行，需要在GitHub仓库设置中配置以下secrets：

### Android Secrets
- `KEYSTORE`: Base64编码的签名密钥库文件
- `KEYSTORE_PASSWORD`: 密钥库密码
- `KEY_ALIAS`: 密钥别名
- `KEY_PASSWORD`: 密钥密码



## 使用方法

### 手动触发
1. 转到仓库的"Actions"选项卡
2. 选择要运行的工作流
3. 点击"Run workflow"按钮

### 自动触发
- 推送到main分支会触发CI/CD工作流
- 创建Pull Request会触发代码质量检查
- 推送标签会触发发布工作流
- 每周一自动运行依赖更新工作流

## 自定义配置

可以根据项目需求修改工作流文件：
- 修改触发条件
- 添加额外的测试步骤
- 更改构建参数
- 调整发布策略

所有工作流文件都位于`.github/workflows/`目录中。