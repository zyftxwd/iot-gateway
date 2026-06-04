package com.iot.backend.dto;

import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备模板：把设备连接参数和默认点表打包，便于快速创建同类设备。
 */
public class DeviceTemplate {
    private String templateKey;
    private String templateName;
    private String protocolType;
    private String deviceType;
    private String description;
    private Boolean builtin;
    private List<String> tags = new ArrayList<>();
    private IotCommDevice device;
    private List<IotCommPoint> points = new ArrayList<>();

    public String getTemplateKey() {
        return templateKey;
    }

    public void setTemplateKey(String templateKey) {
        this.templateKey = templateKey;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean builtin) {
        this.builtin = builtin;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

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
