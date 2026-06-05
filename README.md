# IoT Gateway

工业通讯管理平台。当前版本面向工业网关、设备点表、实时采集、报警闭环、工单流转、维修资料归档和报表分析场景。

> 许可证提示：本仓库源码仅允许学习、研究、内部评估和非商业演示。未经作者书面授权，严禁商用、二次售卖、商业部署、SaaS 托管和改名打包销售。

## 页面预览

### 项目设备点表

![项目设备点表](docs/images/device-console.png)

### MQTT 点位发现

![MQTT 点位发现](docs/images/mqtt-discovery-preview.png)

### 报警事件

![报警事件](docs/images/alarm-events.png)

### 历史数据报表

![历史数据报表](docs/images/report-history.png)

### 工单管理

![工单管理](docs/images/work-orders.png)

### 协议插件

![协议插件](docs/images/protocol-plugins.png)

## 核心能力

- 资产层级：项目、区域、产线、站点分层管理。
- 设备接入：Modbus TCP、MQTT、OPC UA，协议能力由后端元数据驱动。
- 点表管理：手动新增、协议发现、JSON 导入、协议适配模板、批量删除。
- 实时数据：采集状态、实时值展示、读写权限控制、WebSocket 推送。
- 历史数据：按项目、设备、点位、时间范围查询采集历史。
- 报警系统：阈值规则、防抖延迟、恢复延迟、报警事件、转工单闭环。
- 工单系统：派单、接单、处理、验收、关闭、附件、维修资料卡归档。
- 权限系统：账号、部门、角色、项目权限、操作权限控制。
- 报表系统：历史数据报表、报警统计报表、工单维护报表、打印和导出。
- 接口测试：Swagger UI 和前端接口测试页。
- 授权管理：提供基础授权信息展示和系统运行状态管理入口。

## 技术栈

后端：

- Java 8
- Spring Boot 2.7
- MyBatis Plus
- MySQL
- Modbus j2mod
- MQTT Paho
- OPC UA Eclipse Milo
- Spring WebSocket
- springdoc-openapi

前端：

- Vue 3
- Vite
- Element Plus
- ECharts
- Axios

## 项目结构

```text
iot-gateway/
  backend/              Spring Boot 后端
  frontend/             Vue 3 前端
  scripts/              本地启动、停止、模拟器脚本
  deploy/               部署相关配置
  docs/                 项目文档和截图
  docker-compose.yml    Docker 编排草案
  .env.example          环境变量示例
```

## 环境要求

本项目包含 Java 后端、Vue 前端和 MySQL 数据库。首次运行前需要安装：

| 软件 | 建议版本 | 用途 |
| --- | --- | --- |
| JDK | 1.8 | 运行 Spring Boot 后端 |
| Maven | 3.8+ | 下载依赖、启动后端 |
| MySQL | 8.0 | 存储设备、点表、报警、工单和历史数据 |
| Node.js | 18+ | 运行前端开发环境 |
| npm | 9+ | 安装前端依赖 |
| Git | 2.40+ | 拉取代码、版本管理 |

下面命令中的 `C:\path\to\iot-gateway` 需要替换为你本机实际项目目录。

确认命令：

```powershell
java -version
mvn -v
mysql --version
node -v
npm -v
git --version
```

## 数据库初始化

后端默认连接：

```text
数据库：iiot_db
地址：127.0.0.1:3306
账号：root
密码：root
```

配置文件位置：

```text
backend/src/main/resources/application.yml
```

创建空数据库：

```powershell
cd C:\path\to\iot-gateway
mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS iiot_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

如果你的 MySQL 密码不是 `root`，把命令里的 `-proot` 改成自己的密码，例如 `-p你的密码`。

如果 PowerShell 提示 `mysql` 不是可识别命令，需要把 MySQL 的 `bin` 目录加入系统环境变量 `PATH`，例如 `C:\Program Files\MySQL\MySQL Server 8.0\bin`。

后端启动时会通过 Flyway 自动执行数据库迁移脚本：

```text
backend/src/main/resources/db/migration
```

已有旧数据库第一次接入 Flyway 时会以 `V12` 作为基线，只执行后续新增迁移，避免重复执行历史 `ALTER TABLE`。

## 前端启动

首次启动需要安装依赖：

```powershell
cd C:\path\to\iot-gateway\frontend
npm install
```

启动前端开发服务：

```powershell
npm run dev -- --host 127.0.0.1 --port 5173
```

前端访问地址：

```text
http://127.0.0.1:5173
```

## 后端启动

确认 MySQL 已启动并完成数据库初始化，然后执行：

```powershell
cd C:\path\to\iot-gateway\backend
mvn spring-boot:run
```

后端访问地址：

```text
http://127.0.0.1:8080
```

Swagger 接口文档：

```text
http://127.0.0.1:8080/swagger-ui/index.html
```

默认账号：

```text
admin / 123456
```

首次使用默认密码登录后，系统会自动把旧明文密码升级为 BCrypt 哈希。生产环境必须登录后立即修改默认密码，并配置固定的 `IIOT_AUTH_TOKEN_SECRET`。

## 一键开发启动

进入项目根目录：

```powershell
cd C:\path\to\iot-gateway
```

启动前后端：

```powershell
.\scripts\start-dev.ps1
```

停止前后端：

```powershell
.\scripts\stop-dev.ps1
```

访问地址：

- 前端：http://127.0.0.1:5173
- 后端：http://127.0.0.1:8080
- Swagger：http://127.0.0.1:8080/swagger-ui/index.html

## 模拟器

启动 MQTT Broker、MQTT 模拟设备、OPC UA 模拟设备：

```powershell
.\scripts\start-simulators.ps1
```

停止模拟器：

```powershell
.\scripts\stop-simulators.ps1
```

默认模拟器参数：

- MQTT Broker：`127.0.0.1:1883`
- MQTT 读主题：`iiot/test2`
- MQTT 写主题：`iiot/write2`
- OPC UA Server：`opc.tcp://127.0.0.1:4840/UA/IiotTest`
- OPC UA 示例点位：`ns=1;s=Temperature`

更换 MQTT 主题：

```powershell
.\scripts\start-simulators.ps1 -MqttReadTopic "iiot/test2" -MqttWriteTopic "iiot/write2"
```

## Docker

当前 Docker 属于部署草案，正式生产部署前仍建议补齐：

- 初始化 SQL
- 生产 Nginx 配置
- 数据库备份策略
- 日志卷挂载
- 授权文件挂载
- 多采集节点部署方案

启动容器：

```powershell
.\scripts\docker-up.ps1
```

停止容器：

```powershell
.\scripts\docker-down.ps1
```

## 文档

- [系统架构](docs/architecture.md)
- [协议能力和插件边界](docs/protocols.md)
- [部署说明](docs/deployment.md)
- [版本记录](CHANGELOG.md)

## 授权说明

许可证信息见 [LICENSE.md](LICENSE.md)。
