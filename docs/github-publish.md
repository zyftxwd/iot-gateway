# GitHub 发布说明

## 推荐仓库名

```text
iot-gateway
```

## GitHub CLI 登录

当前 GitHub Desktop 已登录不代表 GitHub CLI 已登录。命令行需要单独登录：

```powershell
gh auth login
```

如果命令行提示选择：

```text
GitHub.com
HTTPS
Login with a web browser
```

按提示复制验证码，到浏览器完成授权。

## 创建新仓库并上传

```powershell
cd E:\java\iiot-gateway
git init
git branch -M main
git add .
git commit -m "Initial community source release"
gh repo create iot-gateway --public --source . --remote origin --push
```

如果要先私有：

```powershell
gh repo create iot-gateway --private --source . --remote origin --push
```

## 删除旧仓库

删除仓库是不可逆高风险操作。建议先列出仓库：

```powershell
gh repo list --limit 100
```

确认仓库全名后再删除：

```powershell
gh repo delete OWNER/REPO --confirm
```

不要使用模糊脚本批量删除全部仓库。

## GitHub Desktop 操作方式

如果不想用命令行：

1. 打开 GitHub Desktop。
2. File -> Add local repository。
3. 选择 `E:\java\iiot-gateway`。
4. 点击 Publish repository。
5. 仓库名填写 `iot-gateway`。
6. 根据需要选择 Public 或 Private。

建议第一次发布先 Private，检查文件列表没有日志、密码、授权文件后再改成 Public。
