package com.iot.backend.protocol.modbus;

import com.iot.backend.entity.IotCommPoint;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Modbus 原始寄存器值解析工具。
 * 协议处理器只负责读取寄存器，本类负责把寄存器转成业务值。
 */
public final class ModbusValueParser {

    private ModbusValueParser() {
    }

    public static Object parse(IotCommPoint point, int[] registers) {
        String type = normalize(point.getDataType());
        double coef = point.getCoef() == null ? 1.0 : point.getCoef();

        if ("BOOLEAN".equals(type)) {
            return registers != null && registers.length > 0 && registers[0] != 0;
        }

        if (registers == null || registers.length == 0) {
            return null;
        }

        if ("INT16".equals(type)) {
            return (short) registers[0] * coef;
        }

        if ("UINT16".equals(type)) {
            return (registers[0] & 0xFFFF) * coef;
        }

        if ("INT32".equals(type)) {
            return ByteBuffer.wrap(toFourBytes(point, registers)).getInt() * coef;
        }

        if ("UINT32".equals(type)) {
            long value = ByteBuffer.wrap(toFourBytes(point, registers)).getInt() & 0xFFFFFFFFL;
            return value * coef;
        }

        if ("FLOAT32".equals(type)) {
            return ByteBuffer.wrap(toFourBytes(point, registers)).getFloat() * coef;
        }

        return (registers[0] & 0xFFFF) * coef;
    }

    private static byte[] toFourBytes(IotCommPoint point, int[] registers) {
        int first = registers.length > 0 ? registers[0] : 0;
        int second = registers.length > 1 ? registers[1] : 0;

        byte[] source = new byte[] {
                (byte) ((first >> 8) & 0xFF),
                (byte) (first & 0xFF),
                (byte) ((second >> 8) & 0xFF),
                (byte) (second & 0xFF)
        };

        String byteOrder = normalize(point.getByteOrder());
        if (byteOrder == null || byteOrder.length() == 0) {
            byteOrder = "BA".equalsIgnoreCase(point.getWordOrder()) ? "CDAB" : "ABCD";
        }

        if ("BADC".equals(byteOrder)) {
            return new byte[] { source[1], source[0], source[3], source[2] };
        }
        if ("CDAB".equals(byteOrder)) {
            return new byte[] { source[2], source[3], source[0], source[1] };
        }
        if ("DCBA".equals(byteOrder)) {
            return new byte[] { source[3], source[2], source[1], source[0] };
        }
        return source;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
