# IIoT Backend API 测试样例

服务启动：

```powershell
cd E:\java\iiotbackend
mvn spring-boot:run
```

基础地址：

```text
http://127.0.0.1:8080
```

## 查询设备

```http
GET /api/devices
```

## 查询设备完整配置

```http
GET /api/devices/1/full-config
```

返回内容包含：

- `device`：设备连接信息
- `points`：该设备下的全部点表

## 批量新增设备

```http
POST /api/devices/batch
Content-Type: application/json
```

```json
[
  {
    "deviceName": "2号冷水机",
    "protocolType": "MODBUS_TCP",
    "ipAddress": "192.168.1.20",
    "port": 502,
    "extConfig": "{\"slaveId\":1}"
  },
  {
    "deviceName": "MQTT网关",
    "protocolType": "MQTT",
    "ipAddress": "127.0.0.1",
    "port": 1883,
    "extConfig": "{\"topic\":\"factory/line1/#\"}"
  }
]
```

## 批量新增点表

```http
POST /api/points/batch?deviceId=1
Content-Type: application/json
```

```json
[
  {
    "pointLabel": "主轴转速",
    "pointKey": "spindle_speed",
    "address": "40001",
    "functionCode": 3,
    "slaveId": 1,
    "quantity": 1,
    "dataType": "UInt16",
    "byteOrder": "ABCD",
    "wordOrder": "AB",
    "coef": 1,
    "unit": "rpm",
    "enabled": true,
    "remark": "保持寄存器单字"
  },
  {
    "pointLabel": "冷却液温度",
    "pointKey": "coolant_temp",
    "address": "40002",
    "functionCode": 3,
    "slaveId": 1,
    "quantity": 2,
    "dataType": "Float32",
    "byteOrder": "ABCD",
    "wordOrder": "AB",
    "coef": 0.1,
    "unit": "℃",
    "enabled": true,
    "remark": "保持寄存器双字浮点"
  }
]
```

## 批量导入完整设备配置

```http
POST /api/devices/full-config/batch
Content-Type: application/json
```

```json
[
  {
    "device": {
      "deviceName": "3号空压机",
      "protocolType": "MODBUS_TCP",
      "ipAddress": "192.168.1.30",
      "port": 502,
      "extConfig": "{\"slaveId\":1}"
    },
    "points": [
      {
        "pointLabel": "出口压力",
        "pointKey": "outlet_pressure",
        "address": "40010",
        "functionCode": 3,
        "slaveId": 1,
        "quantity": 2,
        "dataType": "Float32",
        "byteOrder": "ABCD",
        "wordOrder": "AB",
        "coef": 0.01,
        "unit": "MPa",
        "enabled": true
      }
    ]
  }
]
```

## 查询点表

```http
GET /api/points?deviceId=1
```

## 字段说明

| 字段 | 说明 |
| --- | --- |
| `functionCode` | Modbus 功能码：1 线圈，2 离散输入，3 保持寄存器，4 输入寄存器 |
| `slaveId` | Modbus 从站 ID |
| `quantity` | 读取数量；`Float32`、`Int32`、`UInt32` 通常为 2 |
| `dataType` | `Boolean`、`Int16`、`UInt16`、`Int32`、`UInt32`、`Float32` |
| `byteOrder` | 四字节顺序：`ABCD`、`BADC`、`CDAB`、`DCBA` |
| `wordOrder` | 双寄存器字顺序：`AB`、`BA` |
| `coef` | 工程量倍率，默认 1 |
| `enabled` | 是否启用采集 |
