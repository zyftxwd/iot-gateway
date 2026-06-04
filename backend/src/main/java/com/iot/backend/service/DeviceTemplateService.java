package com.iot.backend.service;

import com.iot.backend.dto.DeviceFullConfig;
import com.iot.backend.dto.DeviceTemplate;
import com.iot.backend.dto.DeviceTemplateApplyRequest;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 设备模板服务。
 * 当前先提供内置模板，后续可把模板元数据落库并支持用户自定义模板。
 */
@Service
public class DeviceTemplateService {

    private static final Path HIDDEN_TEMPLATE_FILE = Paths.get("data", "hidden-device-templates.txt").toAbsolutePath();

    @Autowired
    private IotCommDeviceService deviceService;

    public List<DeviceTemplate> listTemplates(String protocolType) {
        List<DeviceTemplate> templates = new ArrayList<>();
        Set<String> hiddenKeys = hiddenTemplateKeys();
        addIfVisible(templates, modbusCompressorTemplate(), hiddenKeys);
        addIfVisible(templates, mqttGatewayTemplate(), hiddenKeys);
        if (protocolType == null || protocolType.trim().length() == 0) {
            return templates;
        }
        String protocol = protocolType.trim().toUpperCase();
        List<DeviceTemplate> filtered = new ArrayList<>();
        for (DeviceTemplate template : templates) {
            if (protocol.equalsIgnoreCase(template.getProtocolType())) {
                filtered.add(template);
            }
        }
        return filtered;
    }

    public boolean deleteTemplate(String templateKey) {
        DeviceTemplate template = getTemplate(templateKey);
        if (template == null) {
            return false;
        }
        Set<String> hiddenKeys = hiddenTemplateKeys();
        hiddenKeys.add(template.getTemplateKey());
        saveHiddenTemplateKeys(hiddenKeys);
        return true;
    }

    private void addIfVisible(List<DeviceTemplate> templates, DeviceTemplate template, Set<String> hiddenKeys) {
        if (!hiddenKeys.contains(template.getTemplateKey())) {
            templates.add(template);
        }
    }

    private Set<String> hiddenTemplateKeys() {
        Set<String> keys = new HashSet<>();
        if (!Files.exists(HIDDEN_TEMPLATE_FILE)) {
            return keys;
        }
        try {
            for (String line : Files.readAllLines(HIDDEN_TEMPLATE_FILE, StandardCharsets.UTF_8)) {
                String key = line == null ? "" : line.trim();
                if (key.length() > 0) {
                    keys.add(key);
                }
            }
        } catch (IOException ignored) {
        }
        return keys;
    }

    private void saveHiddenTemplateKeys(Set<String> keys) {
        try {
            Files.createDirectories(HIDDEN_TEMPLATE_FILE.getParent());
            Files.write(HIDDEN_TEMPLATE_FILE, keys, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to save hidden template keys", ex);
        }
    }

    public DeviceTemplate getTemplate(String templateKey) {
        for (DeviceTemplate template : listTemplates(null)) {
            if (template.getTemplateKey().equalsIgnoreCase(templateKey)) {
                return template;
            }
        }
        return null;
    }

    public DeviceFullConfig applyTemplate(String templateKey, DeviceTemplateApplyRequest request) {
        DeviceTemplate template = getTemplate(templateKey);
        if (template == null) {
            throw new IllegalArgumentException("template not found: " + templateKey);
        }
        if (request.getProjectId() == null || request.getGroupId() == null) {
            throw new IllegalArgumentException("projectId and groupId are required");
        }

        DeviceFullConfig config = new DeviceFullConfig();
        IotCommDevice device = copyDevice(template.getDevice());
        device.setProjectId(request.getProjectId());
        device.setGroupId(request.getGroupId());
        if (request.getDeviceName() != null && request.getDeviceName().trim().length() > 0) {
            device.setDeviceName(request.getDeviceName().trim());
        }
        if (request.getIpAddress() != null && request.getIpAddress().trim().length() > 0) {
            device.setIpAddress(request.getIpAddress().trim());
        }
        if (request.getPort() != null && request.getPort() > 0) {
            device.setPort(request.getPort());
        }
        config.setDevice(device);
        config.setPoints(copyPoints(template.getPoints()));

        List<DeviceFullConfig> created = deviceService.createFullConfigs(Arrays.asList(config));
        return created == null || created.isEmpty() ? config : created.get(0);
    }

    private DeviceTemplate modbusCompressorTemplate() {
        DeviceTemplate template = new DeviceTemplate();
        template.setTemplateKey("modbus_air_compressor");
        template.setTemplateName("Modbus 空压机基础模板");
        template.setProtocolType("MODBUS_TCP");
        template.setDeviceType("PLC");
        template.setDescription("包含出口温度、排气压力、运行频率、启停和频率设定等常见寄存器点位。");
        template.setBuiltin(true);
        template.setTags(Arrays.asList("Modbus", "空压机", "寄存器"));

        IotCommDevice device = new IotCommDevice();
        device.setDeviceName("Modbus 空压机");
        device.setDeviceType("PLC");
        device.setProtocolType("MODBUS_TCP");
        device.setIpAddress("127.0.0.1");
        device.setPort(502);
        device.setCollectIntervalMs(1000);
        device.setHistoryEnabled(true);
        device.setHistoryMode("INTERVAL_CHANGE");
        device.setHistoryIntervalMs(300000);
        device.setStoreOnChange(true);
        device.setExtConfig("{\"slaveId\":1}");
        template.setDevice(device);

        template.setPoints(Arrays.asList(
                point("出口温度", "outlet_temperature", "40001", 3, "Float32", 2, "℃", "READ_ONLY"),
                point("排气压力", "discharge_pressure", "40003", 3, "Float32", 2, "MPa", "READ_ONLY"),
                point("运行频率", "run_frequency", "40005", 3, "Float32", 2, "Hz", "READ_ONLY"),
                point("风机启停", "fan_start_stop", "00001", 1, "Boolean", 0, "", "READ_WRITE"),
                point("频率设定", "frequency_setpoint", "40011", 3, "Float32", 2, "Hz", "READ_WRITE")
        ));
        return template;
    }

    private DeviceTemplate mqttGatewayTemplate() {
        DeviceTemplate template = new DeviceTemplate();
        template.setTemplateKey("mqtt_json_gateway");
        template.setTemplateName("MQTT JSON 网关模板");
        template.setProtocolType("MQTT");
        template.setDeviceType("GENERAL");
        template.setDescription("订阅 JSON 遥测数据，内置设定温度和风机启动两个可写命令点位。");
        template.setBuiltin(true);
        template.setTags(Arrays.asList("MQTT", "JSON", "网关"));

        IotCommDevice device = new IotCommDevice();
        device.setDeviceName("MQTT JSON 网关");
        device.setDeviceType("GENERAL");
        device.setProtocolType("MQTT");
        device.setIpAddress("127.0.0.1");
        device.setPort(1883);
        device.setCollectIntervalMs(1000);
        device.setHistoryEnabled(true);
        device.setHistoryMode("INTERVAL_CHANGE");
        device.setHistoryIntervalMs(300000);
        device.setStoreOnChange(true);
        device.setExtConfig("{\"topic\":\"iiot/test\",\"publishTopic\":\"iiot/write\",\"ackTopic\":\"iiot/write/ack\",\"writablePointKeys\":\"setpoint,fan_start\",\"writeConfirmTimeoutMs\":3000,\"staleTimeoutMs\":5000}");
        template.setDevice(device);

        template.setPoints(Arrays.asList(
                mqttPoint("出口温度", "temperature", "temperature", "Float32", 2, "℃", "READ_ONLY"),
                mqttPoint("环境湿度", "humidity", "humidity", "Float32", 2, "%RH", "READ_ONLY"),
                mqttPoint("排气压力", "pressure", "pressure", "Float32", 2, "MPa", "READ_ONLY"),
                mqttPoint("设定温度", "setpoint", "setpoint", "Float32", 2, "℃", "READ_WRITE"),
                mqttPoint("风机启动", "fan_start", "fan_start", "Boolean", 0, "", "READ_WRITE")
        ));
        return template;
    }

    private IotCommPoint point(String label, String key, String address, int functionCode, String dataType,
                               int decimalPlaces, String unit, String accessMode) {
        IotCommPoint point = basePoint(label, key, address, dataType, decimalPlaces, unit, accessMode);
        point.setFunctionCode(functionCode);
        point.setQuantity(("Float32".equalsIgnoreCase(dataType) || "Int32".equalsIgnoreCase(dataType) || "UInt32".equalsIgnoreCase(dataType)) ? 2 : 1);
        point.setSlaveId(1);
        return point;
    }

    private IotCommPoint mqttPoint(String label, String key, String address, String dataType,
                                   int decimalPlaces, String unit, String accessMode) {
        IotCommPoint point = basePoint(label, key, address, dataType, decimalPlaces, unit, accessMode);
        point.setFunctionCode(0);
        point.setQuantity(1);
        point.setSlaveId(1);
        return point;
    }

    private IotCommPoint basePoint(String label, String key, String address, String dataType,
                                   int decimalPlaces, String unit, String accessMode) {
        IotCommPoint point = new IotCommPoint();
        point.setPointLabel(label);
        point.setPointKey(key);
        point.setAddress(address);
        point.setDataType(dataType);
        point.setByteOrder("ABCD");
        point.setWordOrder("AB");
        point.setCoef(1.0);
        point.setDecimalPlaces(decimalPlaces);
        point.setUnit(unit);
        point.setEnabled(true);
        point.setAccessMode(accessMode);
        point.setHistoryEnabled(true);
        point.setHistoryMode("INHERIT");
        point.setStoreOnChange(true);
        return point;
    }

    private IotCommDevice copyDevice(IotCommDevice source) {
        IotCommDevice target = new IotCommDevice();
        target.setProjectId(source.getProjectId());
        target.setGroupId(source.getGroupId());
        target.setDeviceName(source.getDeviceName());
        target.setDeviceType(source.getDeviceType());
        target.setProtocolType(source.getProtocolType());
        target.setIpAddress(source.getIpAddress());
        target.setPort(source.getPort());
        target.setCollectIntervalMs(source.getCollectIntervalMs());
        target.setHistoryEnabled(source.getHistoryEnabled());
        target.setHistoryMode(source.getHistoryMode());
        target.setHistoryIntervalMs(source.getHistoryIntervalMs());
        target.setChangeThreshold(source.getChangeThreshold());
        target.setStoreOnChange(source.getStoreOnChange());
        target.setExtConfig(source.getExtConfig());
        target.setRemark(source.getRemark());
        return target;
    }

    private List<IotCommPoint> copyPoints(List<IotCommPoint> source) {
        List<IotCommPoint> points = new ArrayList<>();
        if (source == null) {
            return points;
        }
        for (IotCommPoint item : source) {
            IotCommPoint point = basePoint(
                    item.getPointLabel(),
                    item.getPointKey(),
                    item.getAddress(),
                    item.getDataType(),
                    item.getDecimalPlaces() == null ? 2 : item.getDecimalPlaces(),
                    item.getUnit(),
                    item.getAccessMode()
            );
            point.setFunctionCode(item.getFunctionCode());
            point.setSlaveId(item.getSlaveId());
            point.setQuantity(item.getQuantity());
            point.setRemark(item.getRemark());
            points.add(point);
        }
        return points;
    }
}
