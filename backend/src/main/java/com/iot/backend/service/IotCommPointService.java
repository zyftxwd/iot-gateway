package com.iot.backend.service;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.backend.dto.DeviceFullConfig;
import com.iot.backend.dto.PointImportPreviewResult;
import com.iot.backend.dto.PointImportPreviewRow;
import com.iot.backend.dto.PointImportResult;
import com.iot.backend.dto.PointRuntimeValue;
import com.iot.backend.entity.IotAlarmEvent;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.mapper.IotAlarmEventMapper;
import com.iot.backend.mapper.IotCommPointMapper;
import com.iot.backend.protocol.IProtocolHandler;
import com.iot.backend.protocol.ProtocolBrowseNode;
import com.iot.backend.protocol.ProtocolHandlerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IotCommPointService {

    @Autowired
    private IotCommPointMapper pointMapper;

    @Autowired
    private IotAlarmEventMapper alarmMapper;

    @Autowired
    private IotCommDeviceService deviceService;

    @Autowired
    private ProtocolHandlerFactory protocolFactory;

    @Autowired
    private PointRuntimeDataService runtimeDataService;

    @Autowired
    private LicenseService licenseService;

    public List<IotCommPoint> listPoints(Long deviceId, String keyword) {
        QueryWrapper<IotCommPoint> wrapper = new QueryWrapper<>();
        if (deviceId != null) {
            wrapper.eq("comm_device_id", deviceId);
        }
        if (keyword != null && keyword.trim().length() > 0) {
            String value = keyword.trim();
            wrapper.and(w -> w.like("point_label", value)
                    .or()
                    .like("point_key", value)
                    .or()
                    .like("address", value));
        }
        wrapper.orderByAsc("comm_device_id").orderByAsc("id");
        return pointMapper.selectList(wrapper);
    }

    public IotCommPoint getPoint(Long id) {
        return pointMapper.selectById(id);
    }

    public IotCommPoint createPoint(IotCommPoint point) {
        licenseService.assertPointAllowed(1);
        normalizePointForCreate(point);
        validatePointRules(point);
        pointMapper.insert(point);
        return point;
    }

    public List<IotCommPoint> createPoints(Long deviceId, List<IotCommPoint> points) {
        if (points == null) {
            return null;
        }
        licenseService.assertPointAllowed(points.size());
        for (IotCommPoint point : points) {
            if (deviceId != null) {
                point.setCommDeviceId(deviceId);
            }
            createPoint(point);
        }
        return points;
    }

    public PointImportResult importFromExcel(Long deviceId, InputStream inputStream) {
        PointImportPreviewResult preview = previewImportExcel(deviceId, inputStream);
        return confirmImport(deviceId, "CREATE", preview.getRows());
    }

    public PointImportPreviewResult previewImportExcel(Long deviceId, InputStream inputStream) {
        ExcelReader reader = ExcelUtil.getReader(inputStream);
        List<Map<String, Object>> rows = reader.readAll();
        PointImportPreviewResult result = new PointImportPreviewResult();

        int rowNumber = 1;
        for (Map<String, Object> row : rows) {
            rowNumber++;
            PointImportPreviewRow previewRow = new PointImportPreviewRow();
            previewRow.setRowNumber(rowNumber);
            try {
                IotCommPoint point = rowToPoint(deviceId, row);
                normalizePointForCreate(point);
                validateImportPoint(point);
                validatePointRules(point);
                IotCommPoint existing = findDuplicatePoint(deviceId, point.getPointKey());
                previewRow.setPoint(point);
                previewRow.setDuplicate(existing != null);
                previewRow.setExistingPointId(existing == null ? null : existing.getId());
                previewRow.setValid(true);
            } catch (Exception e) {
                previewRow.setValid(false);
                previewRow.setDuplicate(false);
                previewRow.getErrors().add(e.getMessage());
            }
            result.getRows().add(previewRow);
        }

        fillPreviewCounts(result);
        return result;
    }

    public PointImportResult confirmImport(Long deviceId, String duplicateStrategy, List<PointImportPreviewRow> rows) {
        PointImportResult result = new PointImportResult();
        String strategy = duplicateStrategy == null ? "SKIP" : duplicateStrategy.trim().toUpperCase();
        if (rows == null) {
            return result;
        }

        for (PointImportPreviewRow row : rows) {
            if (!Boolean.TRUE.equals(row.getValid()) || row.getPoint() == null) {
                result.setFailCount(result.getFailCount() + 1);
                result.getErrors().add("第 " + row.getRowNumber() + " 行：未通过校验");
                continue;
            }
            try {
                IotCommPoint point = row.getPoint();
                point.setCommDeviceId(deviceId);
                IotCommPoint existing = findDuplicatePoint(deviceId, point.getPointKey());
                if (existing != null && "SKIP".equals(strategy)) {
                    continue;
                }
                if (existing != null && "OVERWRITE".equals(strategy)) {
                    updatePoint(existing.getId(), point);
                } else {
                    createPoint(point);
                }
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                result.setFailCount(result.getFailCount() + 1);
                result.getErrors().add("第 " + row.getRowNumber() + " 行：" + e.getMessage());
            }
        }
        return result;
    }

    public IotCommPoint updatePoint(Long id, IotCommPoint point) {
        point.setId(id);
        normalizePointForUpdate(point);
        validatePointRules(point);
        pointMapper.updateById(point);
        return pointMapper.selectById(id);
    }

    public boolean deletePoint(Long id) {
        return pointMapper.deleteById(id) > 0;
    }

    public int deletePoints(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return pointMapper.deleteBatchIds(ids);
    }

    public int deleteByDeviceId(Long deviceId) {
        return pointMapper.delete(new LambdaQueryWrapper<IotCommPoint>().eq(IotCommPoint::getCommDeviceId, deviceId));
    }

    public List<PointRuntimeValue> listRuntimeValues(Long deviceId) {
        List<PointRuntimeValue> values = runtimeDataService.buildRuntimeValues(deviceId, listPoints(deviceId, null));
        enrichPointCollectErrors(deviceId, values);
        return values;
    }

    private void enrichPointCollectErrors(Long deviceId, List<PointRuntimeValue> values) {
        if (deviceId == null || values == null || values.isEmpty()) {
            return;
        }

        List<IotAlarmEvent> alarms = alarmMapper.selectList(new LambdaQueryWrapper<IotAlarmEvent>()
                .eq(IotAlarmEvent::getDeviceId, deviceId)
                .eq(IotAlarmEvent::getAlarmType, "POINT_COLLECT_ERROR")
                .eq(IotAlarmEvent::getStatus, "ACTIVE"));
        if (alarms == null || alarms.isEmpty()) {
            return;
        }

        Map<Long, IotAlarmEvent> alarmByPointId = new HashMap<>();
        for (IotAlarmEvent alarm : alarms) {
            if (alarm.getPointId() != null && !alarmByPointId.containsKey(alarm.getPointId())) {
                alarmByPointId.put(alarm.getPointId(), alarm);
            }
        }

        for (PointRuntimeValue value : values) {
            if (value == null || value.getPoint() == null || value.getPoint().getId() == null) {
                continue;
            }
            IotAlarmEvent alarm = alarmByPointId.get(value.getPoint().getId());
            if (alarm == null) {
                continue;
            }
            value.setCollectStatus("ERROR");
            value.setCollectErrorMessage(alarm.getMessage());
            value.setCollectErrorTime(alarm.getLastTime());
        }
    }

    public List<IotCommPoint> discoverPoints(Long deviceId) {
        PointImportPreviewResult preview = previewDiscoverPoints(deviceId);
        List<IotCommPoint> created = new ArrayList<>();
        for (PointImportPreviewRow row : preview.getRows()) {
            if (!Boolean.TRUE.equals(row.getValid()) || Boolean.TRUE.equals(row.getDuplicate()) || row.getPoint() == null) {
                continue;
            }
            row.getPoint().setCommDeviceId(deviceId);
            created.add(createPoint(row.getPoint()));
        }
        return created;
    }

    public PointImportPreviewResult previewDiscoverPoints(Long deviceId) {
        DeviceFullConfig config = deviceService.getFullConfig(deviceId);
        if (config == null || config.getDevice() == null) {
            throw new IllegalArgumentException("device not found");
        }

        IProtocolHandler handler = protocolFactory.getHandler(config.getDevice().getProtocolType());
        try {
            Map<String, Object> connectParams = parseExtConfig(config.getDevice().getExtConfig());
            connectParams.put("_deviceId", config.getDevice().getId());
            boolean connected = handler.connect(
                    config.getDevice().getIpAddress(),
                    config.getDevice().getPort() == null ? 0 : config.getDevice().getPort(),
                    connectParams
            );
            if (!connected) {
                throw new IllegalArgumentException("protocol connect failed");
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Map<String, Object> extParams = connectParams;
            Set<String> writablePointKeys = parseWritablePointKeys(extParams.get("writablePointKeys"));
            Map<String, Object> values = handler.readData(config.getPoints());
            PointImportPreviewResult result = new PointImportPreviewResult();

            Set<String> reservedKeys = new HashSet<>();
            reservedKeys.add("_collectStatus");
            reservedKeys.add("timestamp");
            reservedKeys.add("deviceId");
            reservedKeys.add("deviceName");
            reservedKeys.add("protocolType");
            reservedKeys.add("collectCostMs");
            reservedKeys.add("collectorNode");
            reservedKeys.add("last_update");
            reservedKeys.add("_topic");
            reservedKeys.add("raw_payload");

            int rowNumber = 1;
            Set<String> discoveredKeys = new HashSet<>();
            if (values != null && !values.isEmpty()) {
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (key == null || reservedKeys.contains(key) || value == null) {
                        continue;
                    }
                    if (key.startsWith("lastCommand") || key.startsWith("_")) {
                        continue;
                    }
                    if (value instanceof Map || value instanceof List) {
                        continue;
                    }

                    discoveredKeys.add(key);
                    IotCommPoint point = buildDiscoveredMqttPoint(deviceId, key, value, writablePointKeys.contains(key));
                    PointImportPreviewRow row = buildPreviewRow(deviceId, rowNumber++, point, value);
                    result.getRows().add(row);
                }
            }

            if ("MQTT".equalsIgnoreCase(config.getDevice().getProtocolType())) {
                for (String key : writablePointKeys) {
                    if (discoveredKeys.contains(key)) {
                        continue;
                    }
                    IotCommPoint point = buildWritableMqttCommandPoint(deviceId, key);
                    PointImportPreviewRow row = buildPreviewRow(deviceId, rowNumber++, point, "命令点位");
                    result.getRows().add(row);
                }
            }

            fillPreviewCounts(result);
            return result;
        } finally {
            handler.disconnect();
        }
    }

    public List<ProtocolBrowseNode> browseDeviceNodes(Long deviceId) {
        DeviceFullConfig config = deviceService.getFullConfig(deviceId);
        if (config == null || config.getDevice() == null) {
            throw new IllegalArgumentException("device not found");
        }

        IProtocolHandler handler = protocolFactory.getHandler(config.getDevice().getProtocolType());
        try {
            Map<String, Object> connectParams = parseExtConfig(config.getDevice().getExtConfig());
            connectParams.put("_deviceId", config.getDevice().getId());
            boolean connected = handler.connect(
                    config.getDevice().getIpAddress(),
                    config.getDevice().getPort() == null ? 0 : config.getDevice().getPort(),
                    connectParams
            );
            if (!connected) {
                throw new IllegalArgumentException("protocol connect failed");
            }
            return handler.browseNodes();
        } finally {
            handler.disconnect();
        }
    }

    public boolean writePoint(Long pointId, Object value) {
        IotCommPoint point = pointMapper.selectById(pointId);
        if (point == null) {
            throw new IllegalArgumentException("point not found");
        }
        if (!"READ_WRITE".equalsIgnoreCase(point.getAccessMode())) {
            throw new IllegalArgumentException("point is read only");
        }

        DeviceFullConfig config = deviceService.getFullConfig(point.getCommDeviceId());
        if (config == null || config.getDevice() == null) {
            throw new IllegalArgumentException("device not found");
        }

        IProtocolHandler handler = protocolFactory.getHandler(config.getDevice().getProtocolType());
        try {
            Map<String, Object> connectParams = parseExtConfig(config.getDevice().getExtConfig());
            connectParams.put("_deviceId", config.getDevice().getId());
            boolean connected = handler.connect(
                    config.getDevice().getIpAddress(),
                    config.getDevice().getPort() == null ? 0 : config.getDevice().getPort(),
                    connectParams
            );
            if (!connected) {
                return false;
            }
            boolean success = handler.writeData(point, value);
            if (success) {
                runtimeDataService.updatePointValue(point.getCommDeviceId(), point.getPointKey(), value);
            }
            return success;
        } finally {
            handler.disconnect();
        }
    }

    private IotCommPoint buildDiscoveredMqttPoint(Long deviceId, String key, Object value, boolean writable) {
        IotCommPoint point = buildBaseMqttPoint(deviceId, key);
        if (writable) {
            point.setPointLabel(writablePointLabel(key));
        }
        point.setDataType(writable ? inferWritableDataType(key) : inferDataType(value));
        point.setDecimalPlaces("Boolean".equalsIgnoreCase(point.getDataType()) ? 0 : (value instanceof Number ? 2 : 0));
        point.setAccessMode(writable ? "READ_WRITE" : "READ_ONLY");
        point.setRemark(writable ? "MQTT 自动发现可写点位" : "MQTT 自动发现点位");
        return point;
    }

    private IotCommPoint buildWritableMqttCommandPoint(Long deviceId, String key) {
        IotCommPoint point = buildBaseMqttPoint(deviceId, key);
        point.setPointLabel(writablePointLabel(key));
        point.setDataType(inferWritableDataType(key));
        point.setDecimalPlaces("Boolean".equalsIgnoreCase(point.getDataType()) ? 0 : 2);
        point.setAccessMode("READ_WRITE");
        point.setRemark("MQTT 写入命令点位，来自设备 writablePointKeys 配置");
        return point;
    }

    private IotCommPoint buildBaseMqttPoint(Long deviceId, String key) {
        IotCommPoint point = new IotCommPoint();
        point.setCommDeviceId(deviceId);
        point.setPointLabel(key);
        point.setPointKey(key);
        point.setAddress(key);
        point.setFunctionCode(0);
        point.setSlaveId(1);
        point.setQuantity(1);
        point.setByteOrder("ABCD");
        point.setWordOrder("AB");
        point.setCoef(1.0);
        point.setEnabled(true);
        point.setHistoryEnabled(true);
        point.setHistoryMode("INHERIT");
        point.setStoreOnChange(true);
        return point;
    }

    private PointImportPreviewRow buildPreviewRow(Long deviceId, int rowNumber, IotCommPoint point, Object sampleValue) {
        PointImportPreviewRow row = new PointImportPreviewRow();
        row.setRowNumber(rowNumber);
        row.setPoint(point);
        row.setSampleValue(sampleValue);
        row.setValid(true);
        IotCommPoint existing = findDuplicatePoint(deviceId, point.getPointKey());
        row.setDuplicate(existing != null);
        row.setExistingPointId(existing == null ? null : existing.getId());
        return row;
    }

    private String pointProtocolType(IotCommPoint point) {
        if (point == null || point.getCommDeviceId() == null) {
            return "MODBUS_TCP";
        }
        IotCommDevice device = deviceService.getDevice(point.getCommDeviceId());
        if (device == null || device.getProtocolType() == null) {
            return "MODBUS_TCP";
        }
        return device.getProtocolType().trim().toUpperCase();
    }

    private boolean isModbusProtocol(String protocolType) {
        return "MODBUS_TCP".equalsIgnoreCase(protocolType);
    }

    private void normalizePointForCreate(IotCommPoint point) {
        String protocolType = pointProtocolType(point);
        boolean modbus = isModbusProtocol(protocolType);
        if (point.getDataType() != null) {
            point.setDataType(point.getDataType().trim());
        }
        if (point.getPointKey() == null || point.getPointKey().trim().length() == 0) {
            point.setPointKey(defaultPointKey(point));
        }
        if (point.getAddress() == null || point.getAddress().trim().length() == 0) {
            point.setAddress(point.getPointKey());
        }

        if (modbus) {
            if (point.getFunctionCode() == null || point.getFunctionCode() < 0) {
                point.setFunctionCode(defaultFunctionCode(point.getDataType()));
            }
            if (point.getSlaveId() == null || point.getSlaveId() <= 0) {
                point.setSlaveId(1);
            }
            if (requiresTwoRegisters(point.getDataType())) {
                point.setQuantity(2);
            } else if (point.getQuantity() == null || point.getQuantity() <= 0) {
                point.setQuantity(1);
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
        normalizeDecimalPlaces(point);
        if (point.getEnabled() == null) {
            point.setEnabled(true);
        }
        if (point.getAccessMode() == null || point.getAccessMode().trim().length() == 0) {
            point.setAccessMode("READ_ONLY");
        } else {
            point.setAccessMode(point.getAccessMode().trim().toUpperCase());
        }
        normalizeHistoryStrategy(point, true);
    }

    private void normalizePointForUpdate(IotCommPoint point) {
        String protocolType = pointProtocolType(point);
        boolean modbus = isModbusProtocol(protocolType);
        if (point.getDataType() != null) {
            point.setDataType(point.getDataType().trim());
        }
        if (point.getPointKey() != null && point.getPointKey().trim().length() == 0) {
            point.setPointKey(defaultPointKey(point));
        }
        if (point.getAddress() != null && point.getAddress().trim().length() == 0 && point.getPointKey() != null) {
            point.setAddress(point.getPointKey());
        }
        if (point.getByteOrder() != null) {
            point.setByteOrder(point.getByteOrder().trim().toUpperCase());
        }
        if (point.getWordOrder() != null) {
            point.setWordOrder(point.getWordOrder().trim().toUpperCase());
        }
        if (point.getAccessMode() != null) {
            point.setAccessMode(point.getAccessMode().trim().toUpperCase());
        }
        if (!modbus) {
            point.setFunctionCode(0);
            point.setSlaveId(1);
            point.setQuantity(1);
            point.setByteOrder("ABCD");
            point.setWordOrder("AB");
        }
        normalizeDecimalPlaces(point);
        normalizeHistoryStrategy(point, false);
    }

    private void normalizeHistoryStrategy(IotCommPoint point, boolean create) {
        if (Boolean.FALSE.equals(point.getHistoryEnabled())) {
            point.setHistoryMode("DISABLED");
            return;
        }
        if (point.getHistoryMode() != null) {
            point.setHistoryMode(point.getHistoryMode().trim().toUpperCase());
        } else if (create) {
            point.setHistoryMode("INHERIT");
        }
        if (point.getHistoryIntervalMs() != null && point.getHistoryIntervalMs() < 1000) {
            point.setHistoryIntervalMs(1000);
        }
        if (point.getStoreOnChange() == null && create) {
            point.setStoreOnChange(true);
        }
    }

    private IotCommPoint rowToPoint(Long deviceId, Map<String, Object> row) {
        IotCommPoint point = new IotCommPoint();
        point.setCommDeviceId(deviceId);
        point.setPointLabel(text(row, "点位名称"));
        point.setPointKey(text(row, "数据标识"));
        point.setAddress(textAny(row, "地址", "JSON字段路径", "字段路径", "NodeId", "OPC NodeId"));
        point.setFunctionCode(integer(row, "功能码"));
        point.setSlaveId(integer(row, "从站"));
        point.setQuantity(integer(row, "长度"));
        point.setDataType(text(row, "数据类型"));
        point.setByteOrder(text(row, "字节序"));
        point.setWordOrder(text(row, "字序"));
        point.setCoef(decimal(row, "倍率"));
        point.setDecimalPlaces(integer(row, "小数位数"));
        point.setUnit(text(row, "单位"));
        point.setAccessMode(accessMode(text(row, "读写权限")));
        point.setEnabled(enabled(text(row, "启用")));
        point.setRemark(text(row, "点位描述"));
        return point;
    }

    private void validateImportPoint(IotCommPoint point) {
        if (point.getCommDeviceId() == null) {
            throw new IllegalArgumentException("缺少 deviceId");
        }
        if (point.getPointLabel() == null || point.getPointLabel().trim().length() == 0) {
            throw new IllegalArgumentException("点位名称不能为空");
        }
        if (point.getAddress() == null || point.getAddress().trim().length() == 0) {
            throw new IllegalArgumentException("地址或字段路径不能为空");
        }
    }

    private void validatePointRules(IotCommPoint point) {
        if (point.getCommDeviceId() == null) {
            throw new IllegalArgumentException("缺少设备");
        }
        String protocolType = pointProtocolType(point);
        boolean modbus = isModbusProtocol(protocolType);
        if (point.getPointLabel() == null || point.getPointLabel().trim().length() == 0) {
            throw new IllegalArgumentException("点位名称不能为空");
        }
        String dataType = point.getDataType();
        if (dataType == null || dataType.trim().length() == 0) {
            throw new IllegalArgumentException("数据类型不能为空");
        }
        if (!("Boolean".equalsIgnoreCase(dataType)
                || "Int16".equalsIgnoreCase(dataType)
                || "UInt16".equalsIgnoreCase(dataType)
                || "Int32".equalsIgnoreCase(dataType)
                || "UInt32".equalsIgnoreCase(dataType)
                || "Float32".equalsIgnoreCase(dataType)
                || "String".equalsIgnoreCase(dataType))) {
            throw new IllegalArgumentException("数据类型不支持：" + dataType);
        }
        if (modbus && (point.getFunctionCode() == null || point.getFunctionCode() < 1 || point.getFunctionCode() > 16)) {
            throw new IllegalArgumentException("Modbus 功能码范围必须为 1-16");
        }
        if (!modbus && point.getFunctionCode() != null && point.getFunctionCode() != 0) {
            throw new IllegalArgumentException(protocolType + " 点位不使用 Modbus 功能码");
        }
        if (point.getCoef() != null && point.getCoef() == 0) {
            throw new IllegalArgumentException("倍率不能为 0");
        }
    }

    private IotCommPoint findDuplicatePoint(Long deviceId, String pointKey) {
        if (deviceId == null || pointKey == null || pointKey.trim().length() == 0) {
            return null;
        }
        return pointMapper.selectOne(new LambdaQueryWrapper<IotCommPoint>()
                .eq(IotCommPoint::getCommDeviceId, deviceId)
                .eq(IotCommPoint::getPointKey, pointKey)
                .last("LIMIT 1"));
    }

    private void fillPreviewCounts(PointImportPreviewResult result) {
        result.setTotalCount(result.getRows().size());
        result.setValidCount(0);
        result.setInvalidCount(0);
        result.setDuplicateCount(0);
        for (PointImportPreviewRow row : result.getRows()) {
            if (Boolean.TRUE.equals(row.getValid())) {
                result.setValidCount(result.getValidCount() + 1);
            } else {
                result.setInvalidCount(result.getInvalidCount() + 1);
            }
            if (Boolean.TRUE.equals(row.getDuplicate())) {
                result.setDuplicateCount(result.getDuplicateCount() + 1);
            }
        }
    }

    private int defaultFunctionCode(String dataType) {
        if ("String".equalsIgnoreCase(dataType)) {
            return 0;
        }
        if ("Boolean".equalsIgnoreCase(dataType)) {
            return 1;
        }
        return 3;
    }

    private boolean requiresTwoRegisters(String dataType) {
        return "Int32".equalsIgnoreCase(dataType)
                || "UInt32".equalsIgnoreCase(dataType)
                || "Float32".equalsIgnoreCase(dataType);
    }

    private String inferDataType(Object value) {
        if (value instanceof Boolean) {
            return "Boolean";
        }
        if (value instanceof Number) {
            return "Float32";
        }
        return "String";
    }

    private String inferWritableDataType(String key) {
        String value = key == null ? "" : key.toLowerCase();
        if (value.contains("start") || value.contains("stop") || value.contains("run")
                || value.contains("enable") || value.contains("switch") || value.contains("fan")) {
            return "Boolean";
        }
        return "Float32";
    }

    private String writablePointLabel(String key) {
        if ("setpoint".equalsIgnoreCase(key)) {
            return "设定值";
        }
        if ("fan_start".equalsIgnoreCase(key)) {
            return "风机启动";
        }
        return key;
    }

    private Map<String, Object> parseExtConfig(String extConfig) {
        if (extConfig == null || extConfig.trim().length() == 0) {
            return new java.util.HashMap<>();
        }
        try {
            return JSON.parseObject(extConfig, java.util.Map.class);
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }

    private Set<String> parseWritablePointKeys(Object value) {
        Set<String> keys = new HashSet<>();
        if (value == null || String.valueOf(value).trim().length() == 0) {
            keys.add("setpoint");
            keys.add("fan_start");
            return keys;
        }
        String[] parts = String.valueOf(value).split(",");
        for (String part : parts) {
            String key = part == null ? "" : part.trim();
            if (key.length() > 0) {
                keys.add(key);
            }
        }
        return keys;
    }

    private String defaultPointKey(IotCommPoint point) {
        String source = point.getAddress();
        if (source == null || source.trim().length() == 0) {
            source = point.getPointLabel();
        }
        if (source == null || source.trim().length() == 0) {
            source = String.valueOf(System.currentTimeMillis());
        }
        return "point_" + source.trim().replaceAll("[^A-Za-z0-9_\\.\\[\\]]", "_");
    }

    private void normalizeDecimalPlaces(IotCommPoint point) {
        if (point.getDecimalPlaces() == null) {
            point.setDecimalPlaces(2);
            return;
        }
        if (point.getDecimalPlaces() < 0) {
            point.setDecimalPlaces(0);
        }
        if (point.getDecimalPlaces() > 6) {
            point.setDecimalPlaces(6);
        }
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString().trim();
    }

    private String textAny(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            String value = text(row, key);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return null;
    }

    private Integer integer(Map<String, Object> row, String key) {
        String value = text(row, key);
        if (value == null || value.length() == 0) {
            return null;
        }
        return (int) Double.parseDouble(value);
    }

    private Double decimal(Map<String, Object> row, String key) {
        String value = text(row, key);
        if (value == null || value.length() == 0) {
            return null;
        }
        return Double.parseDouble(value);
    }

    private String accessMode(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        String text = value.trim();
        if ("读写".equals(text) || "READ_WRITE".equalsIgnoreCase(text) || "rw".equalsIgnoreCase(text)) {
            return "READ_WRITE";
        }
        return "READ_ONLY";
    }

    private Boolean enabled(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        String text = value.trim();
        return !("否".equals(text) || "false".equalsIgnoreCase(text) || "0".equals(text));
    }
}
