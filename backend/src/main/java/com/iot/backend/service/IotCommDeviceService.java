package com.iot.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.iot.backend.dto.DeviceFullConfig;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.mapper.IotCommDeviceMapper;
import com.iot.backend.mapper.IotCommPointMapper;
import com.iot.backend.protocol.ProtocolHandlerFactory;
import com.iot.backend.protocol.mqtt.MqttProtocolHandlerImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

/**
 * 设备配置服务。
 * 这一层负责处理设备和点表之间的关系，Controller 只负责接收 HTTP 请求。
 */
@Service
public class IotCommDeviceService {

    @Autowired
    private IotCommDeviceMapper deviceMapper;

    @Autowired
    private IotCommPointMapper pointMapper;

    @Autowired
    private ProtocolHandlerFactory protocolHandlerFactory;

    @Autowired
    private LicenseService licenseService;

    /**
     * 获取设备完整配置：先查设备，再查这台设备下面的点表。
     * 采集引擎会用这个方法加载某台设备的全部采集配置。
     */
    public DeviceFullConfig getFullConfig(Long deviceId) {
        IotCommDevice device = deviceMapper.selectById(deviceId);
        if (device == null) {
            return null;
        }

        List<IotCommPoint> points = pointMapper.selectList(
                new LambdaQueryWrapper<IotCommPoint>().eq(IotCommPoint::getCommDeviceId, deviceId)
        );

        DeviceFullConfig config = new DeviceFullConfig();
        config.setDevice(device);
        config.setPoints(points);
        return config;
    }

    /**
     * 查询设备列表。
     * keyword 支持设备名称和 IP 模糊查询；protocolType 支持按协议过滤。
     */
    public List<IotCommDevice> listDevices(String keyword, String protocolType, Long projectId, Long groupId) {
        return listDevices(keyword, protocolType, projectId, groupId, null);
    }

    public List<IotCommDevice> listDevices(String keyword, String protocolType, Long projectId, Long groupId, List<Long> visibleProjectIds) {
        if (visibleProjectIds != null && visibleProjectIds.isEmpty()) {
            return new ArrayList<>();
        }
        if (visibleProjectIds != null && projectId != null && !visibleProjectIds.contains(projectId)) {
            return new ArrayList<>();
        }

        QueryWrapper<IotCommDevice> wrapper = new QueryWrapper<>();

        if (keyword != null && keyword.trim().length() > 0) {
            String value = keyword.trim();
            wrapper.and(w -> w.like("device_name", value)
                    .or()
                    .like("ip_address", value));
        }

        if (protocolType != null && protocolType.trim().length() > 0) {
            wrapper.eq("protocol_type", protocolType.trim().toUpperCase());
        }

        if (projectId != null) {
            wrapper.eq("project_id", projectId);
        } else if (visibleProjectIds != null) {
            wrapper.in("project_id", visibleProjectIds);
        }

        if (groupId != null) {
            wrapper.eq("group_id", groupId);
        }

        wrapper.orderByDesc("id");
        return deviceMapper.selectList(wrapper);
    }

    /**
     * 查询单台设备。
     */
    public IotCommDevice getDevice(Long id) {
        return deviceMapper.selectById(id);
    }

    /**
     * 新增单台设备。
     * 保存前会规范化协议类型，并在端口为空时补常见默认端口。
     */
    public IotCommDevice createDevice(IotCommDevice device) {
        licenseService.assertDeviceAllowed();
        normalizeDeviceForCreate(device);
        deviceMapper.insert(device);
        return device;
    }

    public void updateDeviceStatus(Long id, String status) {
        IotCommDevice device = new IotCommDevice();
        device.setId(id);
        device.setStatus(status);
        deviceMapper.updateById(device);
    }

    public void markCollectSuccess(Long id) {
        long now = System.currentTimeMillis();
        deviceMapper.update(null, new LambdaUpdateWrapper<IotCommDevice>()
                .eq(IotCommDevice::getId, id)
                .set(IotCommDevice::getStatus, "ONLINE")
                .set(IotCommDevice::getLastCollectTime, now)
                .set(IotCommDevice::getLastSuccessTime, now)
                .set(IotCommDevice::getLastErrorMessage, null)
                .set(IotCommDevice::getFailCount, 0));
    }

    public void markCollectFailure(Long id, String errorMessage) {
        IotCommDevice existing = deviceMapper.selectById(id);
        int failCount = existing == null || existing.getFailCount() == null ? 1 : existing.getFailCount() + 1;
        deviceMapper.update(null, new LambdaUpdateWrapper<IotCommDevice>()
                .eq(IotCommDevice::getId, id)
                .set(IotCommDevice::getStatus, "OFFLINE")
                .set(IotCommDevice::getLastCollectTime, System.currentTimeMillis())
                .set(IotCommDevice::getLastErrorMessage, trimError(errorMessage))
                .set(IotCommDevice::getFailCount, failCount));
    }

    /**
     * 批量新增设备。
     * 当前先逐条插入，后续数据量很大时再替换成批处理 SQL。
     */
    public List<IotCommDevice> createDevices(List<IotCommDevice> devices) {
        if (devices == null) {
            return null;
        }

        for (IotCommDevice device : devices) {
            createDevice(device);
        }
        return devices;
    }

    /**
     * 修改设备基础信息。
     */
    public IotCommDevice updateDevice(Long id, IotCommDevice device) {
        IotCommDevice existing = deviceMapper.selectById(id);
        if (existing != null && "MQTT".equalsIgnoreCase(existing.getProtocolType())) {
            MqttProtocolHandlerImpl.closeDeviceConnections(id);
        }
        device.setId(id);
        normalizeDeviceForUpdate(device);
        deviceMapper.updateById(device);
        return deviceMapper.selectById(id);
    }

    /**
     * 删除设备时，先删点表再删设备，避免 iot_comm_point 里残留无效 comm_device_id。
     */
    public boolean deleteDevice(Long id) {
        IotCommDevice existing = deviceMapper.selectById(id);
        if (existing != null && "MQTT".equalsIgnoreCase(existing.getProtocolType())) {
            MqttProtocolHandlerImpl.closeDeviceConnections(id);
        }
        pointMapper.delete(new LambdaQueryWrapper<IotCommPoint>().eq(IotCommPoint::getCommDeviceId, id));
        return deviceMapper.deleteById(id) > 0;
    }

    /**
     * 批量导入完整设备配置。
     * 请求结构是 DeviceFullConfig 列表：每个元素包含 device 和 points。
     */
    public List<DeviceFullConfig> createFullConfigs(List<DeviceFullConfig> configs) {
        if (configs == null) {
            return null;
        }

        for (DeviceFullConfig config : configs) {
            if (config == null || config.getDevice() == null) {
                continue;
            }

            licenseService.assertDeviceAllowed();
            licenseService.assertPointAllowed(config.getPoints() == null ? 0 : config.getPoints().size());
            IotCommDevice device = createDevice(config.getDevice());
            if (config.getPoints() != null) {
                for (IotCommPoint point : config.getPoints()) {
                    point.setCommDeviceId(device.getId());
                    normalizePointForCreate(device.getProtocolType(), point);
                    validatePointRules(device.getProtocolType(), point);
                    pointMapper.insert(point);
                }
            }
        }

        return configs;
    }

    /**
     * 新增时的设备字段整理。
     */
    private void normalizeDeviceForCreate(IotCommDevice device) {
        if (device.getProtocolType() != null) {
            device.setProtocolType(device.getProtocolType().trim().toUpperCase());
        }

        if (!protocolHandlerFactory.isEnabledProtocol(device.getProtocolType())) {
            throw new IllegalArgumentException("protocol not enabled: " + device.getProtocolType());
        }

        if (device.getPort() == null || device.getPort() <= 0) {
            device.setPort(protocolHandlerFactory.defaultPort(device.getProtocolType()));
        }

        if (device.getCollectIntervalMs() == null || device.getCollectIntervalMs() < 1000) {
            device.setCollectIntervalMs(1000);
        }

        normalizeHistoryStrategy(device, true);

        if (device.getDeviceType() == null || device.getDeviceType().trim().length() == 0) {
            device.setDeviceType("GENERAL");
        }

        device.setStatus("UNKNOWN");
    }

    /**
     * 更新时只处理用户传入的字段，避免把没有传的端口覆盖成 0。
     */
    private void normalizeDeviceForUpdate(IotCommDevice device) {
        if (device.getProtocolType() != null) {
            device.setProtocolType(device.getProtocolType().trim().toUpperCase());
            if (!protocolHandlerFactory.isEnabledProtocol(device.getProtocolType())) {
                throw new IllegalArgumentException("protocol not enabled: " + device.getProtocolType());
            }
        }

        if ((device.getPort() == null || device.getPort() <= 0) && device.getProtocolType() != null) {
            device.setPort(protocolHandlerFactory.defaultPort(device.getProtocolType()));
        }
        device.setStatus(null);
        if (device.getCollectIntervalMs() != null && device.getCollectIntervalMs() < 1000) {
            device.setCollectIntervalMs(1000);
        }
        normalizeHistoryStrategy(device, false);
    }

    private void normalizeHistoryStrategy(IotCommDevice device, boolean create) {
        if (create && device.getHistoryEnabled() == null) {
            device.setHistoryEnabled(true);
        }

        if (Boolean.FALSE.equals(device.getHistoryEnabled())) {
            device.setHistoryMode("DISABLED");
            return;
        }

        if (device.getHistoryMode() != null) {
            device.setHistoryMode(device.getHistoryMode().trim().toUpperCase());
        } else if (create) {
            device.setHistoryMode("INTERVAL_CHANGE");
        }

        if (create && (device.getHistoryIntervalMs() == null || device.getHistoryIntervalMs() < 1000)) {
            device.setHistoryIntervalMs(300000);
        } else if (device.getHistoryIntervalMs() != null && device.getHistoryIntervalMs() < 1000) {
            device.setHistoryIntervalMs(1000);
        }

        if (create && device.getStoreOnChange() == null) {
            device.setStoreOnChange(true);
        }
    }

    private String trimError(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }
        return errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage;
    }

    /**
     * 点位新增时的默认值整理。
     */
    private void normalizePointForCreate(String protocolType, IotCommPoint point) {
        if (point.getDataType() != null) {
            point.setDataType(point.getDataType().trim());
        }

        if (point.getPointKey() == null || point.getPointKey().trim().length() == 0) {
            point.setPointKey(defaultPointKey(point));
        }
        if (point.getAddress() == null || point.getAddress().trim().length() == 0) {
            point.setAddress(point.getPointKey());
        }

        if ("MODBUS_TCP".equalsIgnoreCase(protocolType)) {
            if (point.getFunctionCode() == null || point.getFunctionCode() <= 0) {
                point.setFunctionCode(defaultFunctionCode(point.getDataType()));
            }
            if (point.getSlaveId() == null || point.getSlaveId() <= 0) {
                point.setSlaveId(1);
            }
            if (requiresTwoRegisters(point.getDataType())) {
                point.setQuantity(2);
            } else if (point.getQuantity() == null || point.getQuantity() <= 0) {
                point.setQuantity(defaultQuantity(point.getDataType()));
            }
            if (point.getByteOrder() == null || point.getByteOrder().trim().length() == 0) {
                point.setByteOrder("ABCD");
            }
            if (point.getWordOrder() == null || point.getWordOrder().trim().length() == 0) {
                point.setWordOrder("AB");
            }
        } else {
            point.setFunctionCode(0);
            point.setSlaveId(1);
            point.setQuantity(1);
            point.setByteOrder("ABCD");
            point.setWordOrder("AB");
        }

        if (point.getCoef() == null) {
            point.setCoef(1.0);
        }

        if (point.getDecimalPlaces() == null) {
            point.setDecimalPlaces(2);
        }

        if (point.getEnabled() == null) {
            point.setEnabled(true);
        }
        if (point.getAccessMode() == null || point.getAccessMode().trim().length() == 0) {
            point.setAccessMode("READ_ONLY");
        } else {
            point.setAccessMode(point.getAccessMode().trim().toUpperCase());
        }
        if (point.getHistoryEnabled() == null) {
            point.setHistoryEnabled(true);
        }
        if (Boolean.FALSE.equals(point.getHistoryEnabled())) {
            point.setHistoryMode("DISABLED");
        } else if (point.getHistoryMode() == null || point.getHistoryMode().trim().length() == 0) {
            point.setHistoryMode("INHERIT");
        } else {
            point.setHistoryMode(point.getHistoryMode().trim().toUpperCase());
        }
        if (point.getStoreOnChange() == null) {
            point.setStoreOnChange(true);
        }
    }

    private void validatePointRules(String protocolType, IotCommPoint point) {
        if (point.getPointLabel() == null || point.getPointLabel().trim().length() == 0) {
            throw new IllegalArgumentException("点位名称不能为空");
        }
        if (point.getAddress() == null || point.getAddress().trim().length() == 0) {
            throw new IllegalArgumentException("地址、字段路径或 NodeId 不能为空");
        }
        String dataType = point.getDataType();
        if (!("Boolean".equalsIgnoreCase(dataType)
                || "Int16".equalsIgnoreCase(dataType)
                || "UInt16".equalsIgnoreCase(dataType)
                || "Int32".equalsIgnoreCase(dataType)
                || "UInt32".equalsIgnoreCase(dataType)
                || "Float32".equalsIgnoreCase(dataType)
                || "String".equalsIgnoreCase(dataType))) {
            throw new IllegalArgumentException("数据类型不支持：" + dataType);
        }
        if ("MODBUS_TCP".equalsIgnoreCase(protocolType)
                && (point.getFunctionCode() == null || point.getFunctionCode() < 1 || point.getFunctionCode() > 16)) {
            throw new IllegalArgumentException("Modbus 功能码范围必须为 1-16");
        }
        if (!"MODBUS_TCP".equalsIgnoreCase(protocolType)
                && point.getFunctionCode() != null && point.getFunctionCode() != 0) {
            throw new IllegalArgumentException(protocolType + " 点位不使用 Modbus 功能码");
        }
        if (point.getCoef() != null && point.getCoef() == 0) {
            throw new IllegalArgumentException("倍率不能为 0");
        }
    }

    private String defaultPointKey(IotCommPoint point) {
        if (point.getPointLabel() != null && point.getPointLabel().trim().length() > 0) {
            return point.getPointLabel().trim();
        }
        if (point.getAddress() != null && point.getAddress().trim().length() > 0) {
            return point.getAddress().trim();
        }
        return "point_" + System.currentTimeMillis();
    }

    private int defaultFunctionCode(String dataType) {
        if ("Boolean".equalsIgnoreCase(dataType)) {
            return 1;
        }
        return 3;
    }

    private int defaultQuantity(String dataType) {
        if (requiresTwoRegisters(dataType)) {
            return 2;
        }
        return 1;
    }

    private boolean requiresTwoRegisters(String dataType) {
        return "Int32".equalsIgnoreCase(dataType)
                || "UInt32".equalsIgnoreCase(dataType)
                || "Float32".equalsIgnoreCase(dataType);
    }
}
