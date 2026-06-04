import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.protocol.opcua.OpcUaProtocolHandlerImpl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class OpcUaSmokeTest {
    public static void main(String[] args) {
        OpcUaProtocolHandlerImpl handler = new OpcUaProtocolHandlerImpl();
        Map<String, Object> ext = new HashMap<>();
        ext.put("endpointUrl", "opc.tcp://127.0.0.1:4840/UA/IiotTest");
        ext.put("securityPolicy", "None");
        ext.put("authMode", "ANONYMOUS");

        IotCommPoint point = new IotCommPoint();
        point.setPointKey("temperature");
        point.setPointLabel("Temperature");
        point.setAddress("ns=1;s=Temperature");
        point.setDataType("Float32");
        point.setAccessMode("READ_WRITE");

        if (!handler.connect("127.0.0.1", 4840, ext)) {
            throw new IllegalStateException("connect failed");
        }
        try {
            Map<String, Object> before = handler.readData(Arrays.asList(point));
            boolean writeOk = handler.writeData(point, 31.25);
            Map<String, Object> after = handler.readData(Arrays.asList(point));
            System.out.println("before=" + before);
            System.out.println("writeOk=" + writeOk);
            System.out.println("after=" + after);
            if (!writeOk || !after.toString().contains("31.25")) {
                throw new IllegalStateException("OPC UA read/write smoke test failed");
            }
        } finally {
            handler.disconnect();
        }
    }
}
