# 公开版本和保留能力边界

## 为什么要写清楚边界

如果仓库公开在 GitHub，别人可以看到源码。如果使用标准开源许可证，别人通常会获得使用、修改和分发的权利。

但本项目目标是商业运行，部分能力需要保留。因此当前更适合采用：

```text
源码可见社区版 + 商业授权保留
```

这不是严格意义上的 OSI 开源许可证，而是 source-available 模式。

参考：

- OSI 对开源定义要求许可证不能限制商业等使用领域：https://opensource.org/osd
- GitHub 文档说明公开仓库如果没有许可证，默认版权法仍然适用：https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository

## 社区版建议开放

- 基础设备管理
- 基础点表管理
- Modbus TCP 基础采集
- MQTT 基础采集
- OPC UA 基础浏览
- 基础报警
- 基础工单
- 基础报表
- 本地模拟器
- 开发文档

## 建议保留

- 商业授权和加密狗能力
- 高级协议驱动
- 私有协议插件
- 分布式采集节点
- 大规模压力测试方案
- 移动端商业接口编排
- 客户专用报表模板
- 客户专用导入模板
- 生产 Docker 镜像和部署脚本
- 企业级技术支持和二次开发服务

## 仓库描述建议

```text
Industrial IoT gateway platform with device management, protocol collection, alarms, work orders, maintenance cards, reports and permission control.
```

## GitHub Topics 建议

```text
iot
industrial-iot
gateway
modbus
mqtt
opcua
spring-boot
vue3
scada
work-order
alarm-management
```
