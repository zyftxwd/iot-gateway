package com.iot.backend.protocol.modbus;

import com.iot.backend.entity.IotCommPoint;

import java.nio.ByteBuffer;
import java.util.Locale;

public final class ModbusValueEncoder {

    private ModbusValueEncoder() {
    }

    public static int[] encode(IotCommPoint point, Object rawValue) {
        String type = normalize(point.getDataType());
        double coef = point.getCoef() == null || point.getCoef() == 0 ? 1.0 : point.getCoef();
        double engineeringValue = Double.parseDouble(rawValue.toString());
        double protocolValue = engineeringValue / coef;

        if ("INT32".equals(type)) {
            return fromFourBytes(point, ByteBuffer.allocate(4).putInt((int) protocolValue).array());
        }
        if ("UINT32".equals(type)) {
            long value = Math.round(protocolValue) & 0xFFFFFFFFL;
            return fromFourBytes(point, ByteBuffer.allocate(4).putInt((int) value).array());
        }
        if ("FLOAT32".equals(type)) {
            return fromFourBytes(point, ByteBuffer.allocate(4).putFloat((float) protocolValue).array());
        }

        return new int[] { ((int) Math.round(protocolValue)) & 0xFFFF };
    }

    private static int[] fromFourBytes(IotCommPoint point, byte[] ordered) {
        byte[] source = fromConfiguredOrder(point, ordered);
        int first = ((source[0] & 0xFF) << 8) | (source[1] & 0xFF);
        int second = ((source[2] & 0xFF) << 8) | (source[3] & 0xFF);
        return new int[] { first, second };
    }

    private static byte[] fromConfiguredOrder(IotCommPoint point, byte[] ordered) {
        String byteOrder = normalize(point.getByteOrder());
        if (byteOrder.length() == 0) {
            byteOrder = "BA".equalsIgnoreCase(point.getWordOrder()) ? "CDAB" : "ABCD";
        }

        if ("BADC".equals(byteOrder)) {
            return new byte[] { ordered[1], ordered[0], ordered[3], ordered[2] };
        }
        if ("CDAB".equals(byteOrder)) {
            return new byte[] { ordered[2], ordered[3], ordered[0], ordered[1] };
        }
        if ("DCBA".equals(byteOrder)) {
            return new byte[] { ordered[3], ordered[2], ordered[1], ordered[0] };
        }
        return ordered;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
