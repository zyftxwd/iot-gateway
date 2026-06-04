package com.iot.backend.dto;

import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import lombok.Data;
import java.util.List;

/**
 * 工业通讯配置全量包：包含设备基础连接信息 + 测点列表
 */
@Data
public class DeviceFullConfig {
    // 设备连接信息 (IP, Port, Protocol 等)
    private IotCommDevice device;

    // 该设备下的所有测点列表 (点表)
    private List<IotCommPoint> points;


    public IotCommDevice getDevice() {
        return device;
    }

    public void setDevice(IotCommDevice device) {
        this.device = device;
    }

    public List<IotCommPoint> getPoints() {
        return points;
    }

    public void setPoints(List<IotCommPoint> points) {
        this.points = points;
    }
}