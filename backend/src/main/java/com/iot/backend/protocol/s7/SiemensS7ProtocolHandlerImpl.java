package com.iot.backend.protocol.s7;

import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.protocol.IProtocolHandler;
import com.iot.backend.protocol.ProtocolField;
import com.iot.backend.protocol.ProtocolMetadata;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工业协议：西门子 S7 协议接入预留
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SiemensS7ProtocolHandlerImpl implements IProtocolHandler {

    private boolean isConnected = false;

    @Override
    public String getProtocolType() {
        return "SIEMENS_S7";
    }

    @Override
    public ProtocolMetadata metadata() {
        ProtocolMetadata metadata = new ProtocolMetadata("SIEMENS_S7", "Siemens S7", "西门子 S7 协议插件预留，需引入真实 S7 通讯库后启用。", false)
                .capabilities(false, false, false, false, false)
                .addField(ProtocolField.device("ipAddress", "PLC地址", "text", true, "127.0.0.1", "PLC IP"))
                .addField(ProtocolField.device("port", "端口", "number", true, 102, "默认 102"))
                .addField(ProtocolField.ext("rack", "Rack", "number", true, 0, "默认 0"))
                .addField(ProtocolField.ext("slot", "Slot", "number", true, 1, "常见 S7-1200/1500 为 1"));
        metadata.setInstallStatus("PLANNED");
        return metadata;
    }

    @Override
    public boolean connect(String ip, int port, Map<String, Object> extParams) {
        this.isConnected = false;
        return false;
    }

    @Override
    public Map<String, Object> readData(List<IotCommPoint> pointList) {
        Map<String, Object> resultMap = new HashMap<>();

        if (isConnected && pointList != null) {
            // 🌟 工业级模拟：遍历前端/数据库配好的点表，动态生成对应类型的假数据
            for (IotCommPoint point : pointList) {
                double baseValue = 0;
                // 根据配置的数据类型模拟不同的值
                if ("Float32".equalsIgnoreCase(point.getDataType())) {
                    baseValue = 45.5; // 模拟温度或压力
                } else {
                    baseValue = 1500; // 模拟转速或整型
                }

                // 乘以倍率，并用 pointKey 作为最终的 JSON 键
                double finalValue = baseValue * (point.getCoef() != null ? point.getCoef() : 1.0);
                resultMap.put(point.getPointKey(), finalValue);
            }
            resultMap.put("status", "online_mock");
        } else {
            resultMap.put("status", "offline");
        }

        return resultMap;
    }
    @Override
    public void disconnect() {
        this.isConnected = false;
    }
}
