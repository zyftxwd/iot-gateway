# 部署说明

## 本地开发

推荐使用脚本启动：

```powershell
cd E:\java\iiot-gateway
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

## 数据库

后端默认读取 `application.yml` 中的数据库配置，也可以通过环境变量覆盖：

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
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
