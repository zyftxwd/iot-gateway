package com.iot.backend.protocol;

import com.iot.backend.entity.IotCommPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工业协议统一接入标准接口 (所有协议的具体实现都必须遵守这个规矩)
 */
public interface IProtocolHandler {

    /**
     * 亮明身份：告诉工厂你支持什么协议 (例如返回 "MODBUS_TCP" 或 "MQTT")
     */
    String getProtocolType();

    default ProtocolMetadata metadata() {
        return new ProtocolMetadata(getProtocolType(), getProtocolType(), "Protocol handler is installed but not enabled for device configuration.", false);
    }

    /**
     * 建立连接
     * @param ip 设备IP / Broker 地址
     * @param port 端口
     * @param extParams 其他扩展参数 (如站号、Topic 等，用 Map 接收极其灵活)
     * @return 是否连接成功
     */
    boolean connect(String ip, int port, Map<String, Object> extParams);

    /**
     * 统一的数据读取接口
     * @return 屏蔽底层差异，统一返回键值对形式的业务数据 (如 {"temp": 25.5, "speed": 1500})
     */
    Map<String, Object> readData(List<IotCommPoint> pointList);

    default boolean writeData(IotCommPoint point, Object value) {
        throw new UnsupportedOperationException("write not supported");
    }

    default List<ProtocolBrowseNode> browseNodes() {
        return new ArrayList<>();
    }

    /**
     * 断开连接，释放资源
     */
    void disconnect();
}
