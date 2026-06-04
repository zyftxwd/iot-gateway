# Gitee 发布说明

推荐 Gitee 仓库展示名：

```text
工业物联网网关平台
```

推荐仓库路径：

```text
iot-gateway
```

中文展示名适合国内平台展示，英文路径适合 Git 克隆、推送、CI 和脚本。

## 准备 Gitee Token

进入：

```text
https://gitee.com/profile/personal_access_tokens
```

新建私人令牌，建议勾选：

```text
projects
user_info
```

生成后不要发给任何人。

## 自动创建并导入

在项目根目录执行：

```powershell
cd E:\java\iiot-gateway
.\scripts\publish-gitee.ps1
```

脚本会隐藏输入 Gitee Token，并执行：

- 读取当前 Gitee 用户信息。
- 创建 `工业物联网网关平台` 仓库。
- 使用 `iot-gateway` 作为仓库路径。
- 从 GitHub 仓库 `https://github.com/zyftxwd/iot-gateway.git` 导入代码。
- 添加本地 Git 远程 `gitee`。

如果后续要手动推送到 Gitee：

```powershell
git push gitee main
```

如果 Gitee 还在导入同步中，等待几分钟后再检查文件。
