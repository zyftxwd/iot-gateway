package com.iot.backend.protocol.modbus;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import com.ghgande.j2mod.modbus.procimg.Register;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.util.BitVector;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.protocol.IProtocolHandler;
import com.iot.backend.protocol.ProtocolField;
import com.iot.backend.protocol.ProtocolMetadata;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Modbus TCP 协议处理器。
 * 当前实现支持多从站、多功能码，并对同组连续地址做批量读取。
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ModbusProtocolHandlerImpl implements IProtocolHandler {

    private static final int DEFAULT_SLAVE_ID = 1;
    private static final int MAX_REGISTERS_PER_REQUEST = 120;
    private static final int MAX_BITS_PER_REQUEST = 2000;

    private ModbusTCPMaster master;
    private int defaultSlaveId = DEFAULT_SLAVE_ID;
    private boolean connectedStatus = false;

    @Override
    public String getProtocolType() {
        return "MODBUS_TCP";
    }

    @Override
    public ProtocolMetadata metadata() {
        return new ProtocolMetadata("MODBUS_TCP", "Modbus TCP", "Modbus TCP 主站采集，支持多从站、批量寄存器读取和点位写入。", true)
                .capabilities(false, true, true, true, false)
                .addField(ProtocolField.device("ipAddress", "IP/地址", "text", true, "127.0.0.1", "例如：192.168.1.10"))
                .addField(ProtocolField.device("port", "端口", "number", true, 502, "默认 502"))
                .addField(ProtocolField.ext("slaveId", "默认从站", "number", false, 1, "点位未设置从站时使用"));
    }

    @Override
    public boolean connect(String ip, int port, Map<String, Object> extParams) {
        try {
            if (extParams != null && extParams.containsKey("slaveId")) {
                this.defaultSlaveId = Integer.parseInt(extParams.get("slaveId").toString());
            }

            master = new ModbusTCPMaster(ip, port);
            master.connect();
            this.connectedStatus = master.isConnected();
            return this.connectedStatus;
        } catch (Exception e) {
            System.err.println("Modbus connect failed [" + ip + ":" + port + "] - " + e.getMessage());
            this.connectedStatus = false;
            return false;
        }
    }

    @Override
    public Map<String, Object> readData(List<IotCommPoint> pointList) {
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, String> pointErrors = new HashMap<>();

        if (master == null || !this.connectedStatus) {
            resultMap.put("status", "offline");
            return resultMap;
        }

        if (pointList == null || pointList.isEmpty()) {
            resultMap.put("status", "online_no_points");
            return resultMap;
        }

        Map<GroupKey, List<PointReadPlan>> grouped = groupPoints(pointList, pointErrors);
        for (Map.Entry<GroupKey, List<PointReadPlan>> entry : grouped.entrySet()) {
            readGroup(entry.getKey(), entry.getValue(), resultMap, pointErrors);
        }

        int successCount = countReadableValues(pointList, resultMap);
        resultMap.put("status", "online");
        resultMap.put("_readSuccessCount", successCount);
        resultMap.put("_readFailureCount", pointErrors.size());
        if (!pointErrors.isEmpty()) {
            resultMap.put("_pointErrors", pointErrors);
        }

        return resultMap;
    }

    @Override
    public boolean writeData(IotCommPoint point, Object value) {
        if (master == null || !this.connectedStatus || point == null) {
            return false;
        }

        int functionCode = defaultFunctionCode(point);
        int slaveId = point.getSlaveId() == null || point.getSlaveId() <= 0 ? defaultSlaveId : point.getSlaveId();
        int offset = toProtocolOffset(functionCode, point.getAddress());

        try {
            if (functionCode == 1 || functionCode == 5) {
                boolean boolValue = toBoolean(value);
                return master.writeCoil(slaveId, offset, boolValue);
            }

            if (functionCode == 3 || functionCode == 6 || functionCode == 16) {
                int[] values = ModbusValueEncoder.encode(point, value);
                if (values.length == 1) {
                    master.writeSingleRegister(slaveId, offset, new SimpleRegister(values[0]));
                } else {
                    Register[] registers = new Register[values.length];
                    for (int i = 0; i < values.length; i++) {
                        registers[i] = new SimpleRegister(values[i]);
                    }
                    master.writeMultipleRegisters(slaveId, offset, registers);
                }
                return true;
            }
        } catch (Exception e) {
            System.err.println("Modbus write failed: " + e.getMessage());
        }

        return false;
    }

    @Override
    public void disconnect() {
        if (master != null) {
            master.disconnect();
            this.connectedStatus = false;
        }
    }

    private Map<GroupKey, List<PointReadPlan>> groupPoints(List<IotCommPoint> pointList, Map<String, String> pointErrors) {
        Map<GroupKey, List<PointReadPlan>> grouped = new TreeMap<>();

        for (IotCommPoint point : pointList) {
            if (point.getEnabled() != null && !point.getEnabled()) {
                continue;
            }

            try {
                int functionCode = defaultFunctionCode(point);
                int slaveId = point.getSlaveId() == null || point.getSlaveId() <= 0 ? defaultSlaveId : point.getSlaveId();
                int quantity = defaultQuantity(point);
                int offset = toProtocolOffset(functionCode, point.getAddress());

                GroupKey key = new GroupKey(slaveId, functionCode);
                grouped.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new PointReadPlan(point, offset, quantity));
            } catch (Exception e) {
                addPointError(pointErrors, point, "invalid point config: " + e.getMessage());
            }
        }

        for (List<PointReadPlan> plans : grouped.values()) {
            plans.sort(Comparator.comparingInt(p -> p.offset));
        }

        return grouped;
    }

    private void readGroup(GroupKey key, List<PointReadPlan> plans, Map<String, Object> resultMap, Map<String, String> pointErrors) {
        if (key.functionCode == 1 || key.functionCode == 2) {
            readBitGroup(key, plans, resultMap, pointErrors);
            return;
        }

        if (key.functionCode == 3 || key.functionCode == 4) {
            readRegisterGroup(key, plans, resultMap, pointErrors);
            return;
        }

        for (PointReadPlan plan : plans) {
            addPointError(pointErrors, plan.point, "unsupported function code: " + key.functionCode);
        }
    }

    private void readBitGroup(GroupKey key, List<PointReadPlan> plans, Map<String, Object> resultMap, Map<String, String> pointErrors) {
        int index = 0;
        while (index < plans.size()) {
            int start = plans.get(index).offset;
            int end = start;
            int next = index + 1;

            while (next < plans.size()) {
                PointReadPlan candidate = plans.get(next);
                int candidateEnd = candidate.offset + candidate.quantity - 1;
                if (candidateEnd - start + 1 > MAX_BITS_PER_REQUEST) {
                    break;
                }
                end = Math.max(end, candidateEnd);
                next++;
            }

            try {
                BitVector bits = key.functionCode == 1
                        ? master.readCoils(key.slaveId, start, end - start + 1)
                        : master.readInputDiscretes(key.slaveId, start, end - start + 1);

                for (int i = index; i < next; i++) {
                    PointReadPlan plan = plans.get(i);
                    boolean value = bits.getBit(plan.offset - start);
                    resultMap.put(plan.point.getPointKey(), value);
                }
            } catch (Exception batchError) {
                readBitPlansIndividually(key, plans.subList(index, next), resultMap, pointErrors);
            }

            index = next;
        }
    }

    private void readRegisterGroup(GroupKey key, List<PointReadPlan> plans, Map<String, Object> resultMap, Map<String, String> pointErrors) {
        int index = 0;
        while (index < plans.size()) {
            int start = plans.get(index).offset;
            int end = plans.get(index).offset + plans.get(index).quantity - 1;
            int next = index + 1;

            while (next < plans.size()) {
                PointReadPlan candidate = plans.get(next);
                int candidateEnd = candidate.offset + candidate.quantity - 1;
                if (candidateEnd - start + 1 > MAX_REGISTERS_PER_REQUEST) {
                    break;
                }
                end = Math.max(end, candidateEnd);
                next++;
            }

            try {
                Map<Integer, Integer> registerMap = readRegisters(key, start, end - start + 1);
                for (int i = index; i < next; i++) {
                    PointReadPlan plan = plans.get(i);
                    putParsedRegisterValue(plan, registerMap, resultMap, pointErrors);
                }
            } catch (Exception batchError) {
                readRegisterPlansIndividually(key, plans.subList(index, next), resultMap, pointErrors);
            }

            index = next;
        }
    }

    private void readBitPlansIndividually(GroupKey key, List<PointReadPlan> plans, Map<String, Object> resultMap, Map<String, String> pointErrors) {
        for (PointReadPlan plan : plans) {
            try {
                BitVector bits = key.functionCode == 1
                        ? master.readCoils(key.slaveId, plan.offset, plan.quantity)
                        : master.readInputDiscretes(key.slaveId, plan.offset, plan.quantity);
                resultMap.put(plan.point.getPointKey(), bits.getBit(0));
            } catch (Exception e) {
                addPointError(pointErrors, plan.point, "read failed: " + e.getMessage());
            }
        }
    }

    private void readRegisterPlansIndividually(GroupKey key, List<PointReadPlan> plans, Map<String, Object> resultMap, Map<String, String> pointErrors) {
        for (PointReadPlan plan : plans) {
            try {
                Map<Integer, Integer> registerMap = readRegisters(key, plan.offset, plan.quantity);
                putParsedRegisterValue(plan, registerMap, resultMap, pointErrors);
            } catch (Exception e) {
                addPointError(pointErrors, plan.point, "read failed: " + e.getMessage());
            }
        }
    }

    private void putParsedRegisterValue(PointReadPlan plan, Map<Integer, Integer> registerMap, Map<String, Object> resultMap, Map<String, String> pointErrors) {
        try {
            int[] values = new int[plan.quantity];
            for (int j = 0; j < plan.quantity; j++) {
                Integer register = registerMap.get(plan.offset + j);
                values[j] = register == null ? 0 : register;
            }
            resultMap.put(plan.point.getPointKey(), ModbusValueParser.parse(plan.point, values));
        } catch (Exception e) {
            addPointError(pointErrors, plan.point, "parse failed: " + e.getMessage());
        }
    }

    private Map<Integer, Integer> readRegisters(GroupKey key, int start, int quantity) throws Exception {
        Map<Integer, Integer> registerMap = new HashMap<>();

        if (key.functionCode == 4) {
            InputRegister[] registers = master.readInputRegisters(key.slaveId, start, quantity);
            for (int i = 0; i < registers.length; i++) {
                registerMap.put(start + i, registers[i].getValue());
            }
            return registerMap;
        }

        Register[] registers = master.readMultipleRegisters(key.slaveId, start, quantity);
        for (int i = 0; i < registers.length; i++) {
            registerMap.put(start + i, registers[i].getValue());
        }
        return registerMap;
    }

    private int defaultFunctionCode(IotCommPoint point) {
        if (point.getFunctionCode() != null && point.getFunctionCode() > 0) {
            return point.getFunctionCode();
        }
        if ("BOOLEAN".equals(normalize(point.getDataType()))) {
            return 1;
        }
        return 3;
    }

    private int defaultQuantity(IotCommPoint point) {
        if (point.getQuantity() != null && point.getQuantity() > 0) {
            return point.getQuantity();
        }

        String type = normalize(point.getDataType());
        if ("INT32".equals(type) || "UINT32".equals(type) || "FLOAT32".equals(type)) {
            return 2;
        }
        return 1;
    }

    private int toProtocolOffset(int functionCode, String addressText) {
        int address = Integer.parseInt(addressText.trim());
        if (functionCode == 1 && address >= 1 && address < 10000) {
            return address - 1;
        }
        if (functionCode == 2 && address >= 10001 && address < 20000) {
            return address - 10001;
        }
        if (functionCode == 3 && address >= 40001) {
            return address - 40001;
        }
        if (functionCode == 4 && address >= 30001 && address < 40000) {
            return address - 30001;
        }
        return address;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text);
    }

    private int countReadableValues(List<IotCommPoint> pointList, Map<String, Object> resultMap) {
        int count = 0;
        for (IotCommPoint point : pointList) {
            if (point != null && point.getPointKey() != null && resultMap.containsKey(point.getPointKey())) {
                count++;
            }
        }
        return count;
    }

    private void addPointError(Map<String, String> pointErrors, IotCommPoint point, String message) {
        String key = pointErrorKey(point);
        String text = message == null ? "read failed" : message;
        if (text.length() > 200) {
            text = text.substring(0, 200);
        }
        pointErrors.put(key, text);
        System.err.println("Modbus point read failed [" + key + "]: " + text);
    }

    private String pointErrorKey(IotCommPoint point) {
        if (point == null) {
            return "unknown";
        }
        if (point.getPointKey() != null && point.getPointKey().trim().length() > 0) {
            return point.getPointKey().trim();
        }
        if (point.getAddress() != null && point.getAddress().trim().length() > 0) {
            return point.getAddress().trim();
        }
        if (point.getId() != null) {
            return String.valueOf(point.getId());
        }
        return "unknown";
    }

    private static class GroupKey implements Comparable<GroupKey> {
        private final int slaveId;
        private final int functionCode;

        private GroupKey(int slaveId, int functionCode) {
            this.slaveId = slaveId;
            this.functionCode = functionCode;
        }

        @Override
        public int compareTo(GroupKey other) {
            int slaveCompare = Integer.compare(this.slaveId, other.slaveId);
            if (slaveCompare != 0) {
                return slaveCompare;
            }
            return Integer.compare(this.functionCode, other.functionCode);
        }
    }

    private static class PointReadPlan {
        private final IotCommPoint point;
        private final int offset;
        private final int quantity;

        private PointReadPlan(IotCommPoint point, int offset, int quantity) {
            this.point = point;
            this.offset = offset;
            this.quantity = quantity;
        }
    }
}
