# 部署说明

## 本地开发环境

首次运行前建议安装：

- JDK 1.8
- Maven 3.8+
- MySQL 8.0
- Node.js 18+
- npm 9+
- Git 2.40+

下面命令中的 `C:\path\to\iot-gateway` 需要替换为本机实际项目目录。

确认命令：

```powershell
java -version
mvn -v
mysql --version
node -v
npm -v
git --version
```

## 数据库

后端默认数据库配置：

```text
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/iiot_db
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=root
```

初始化数据库：

```powershell
cd C:\path\to\iot-gateway
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS iiot_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

$sqlFiles = Get-ChildItem .\backend\docs\sql\*.sql | Sort-Object Name
foreach ($file in $sqlFiles) {
  Get-Content $file.FullName -Raw | mysql -uroot -proot --default-character-set=utf8mb4 iiot_db
}
```

如果 PowerShell 提示 `mysql` 不是可识别命令，需要把 MySQL 的 `bin` 目录加入系统环境变量 `PATH`。

如果数据库账号或密码不同，可以通过环境变量覆盖：

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

## 前端

```powershell
cd C:\path\to\iot-gateway\frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```

访问：

```text
http://127.0.0.1:5173
```

## 后端

```powershell
cd C:\path\to\iot-gateway\backend
mvn spring-boot:run
```

访问：

```text
http://127.0.0.1:8080
http://127.0.0.1:8080/swagger-ui/index.html
```

## 一键启动

推荐使用脚本启动：

```powershell
cd C:\path\to\iot-gateway
.\scripts\start-dev.ps1
```

停止：

```powershell
.\scripts\stop-dev.ps1
```

## 模拟器

启动 MQTT、OPC UA 模拟器：

```powershell
.\scripts\start-simulators.ps1
```

停止：

```powershell
.\scripts\stop-simulators.ps1
```

## Docker 部署方向

正式部署建议拆成：

- `iot-backend`
- `iot-frontend`
- `mysql`
- `nginx`
- `collector-node` 可选
- `mqtt-broker` 可选

生产环境必须处理：

- 数据库初始化脚本
- 数据库备份
- 上传附件目录挂载
- 日志目录挂载
- 授权文件挂载
- 前后端域名和 HTTPS
- 采集节点健康检查

## 生产安全建议

- 修改默认账号密码。
- 不提交 `.env`、数据库密码、授权文件。
- 对写入点位做权限控制和审计。
- 对公网接口启用 HTTPS。
- 工业现场写入必须经过确认和操作日志。
