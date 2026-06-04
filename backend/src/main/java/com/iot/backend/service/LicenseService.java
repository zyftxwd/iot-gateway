package com.iot.backend.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.backend.dto.LicenseStatus;
import com.iot.backend.mapper.IotCommDeviceMapper;
import com.iot.backend.mapper.IotCommPointMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

@Service
public class LicenseService {

    @Value("${industrial.license.file:./iiot-license.json}")
    private String licenseFile;

    @Autowired
    private IotCommDeviceMapper deviceMapper;

    @Autowired
    private IotCommPointMapper pointMapper;

    private JSONObject licensePayload;
    private String machineCode;

    @PostConstruct
    public void init() {
        machineCode = buildMachineCode();
        loadLicenseFile();
    }

    public LicenseStatus status() {
        LicenseStatus status = new LicenseStatus();
        status.setMachineCode(machineCode);
        status.setUsedDevices(Math.toIntExact(deviceMapper.selectCount(new QueryWrapper<>())));
        status.setUsedPoints(Math.toIntExact(pointMapper.selectCount(new QueryWrapper<>())));

        if (licensePayload == null) {
            fillDevelopmentLicense(status);
            return status;
        }

        status.setMode(text("mode", "SOFTWARE"));
        status.setLicenseNo(text("licenseNo", "-"));
        status.setIssuedAt(number("issuedAt", null));
        status.setExpiresAt(number("expiresAt", null));
        status.setMaxDevices(integer("maxDevices", 100));
        status.setMaxPoints(integer("maxPoints", 10000));
        status.setEnabledProtocols(protocols());

        boolean valid = true;
        String boundMachine = text("machineCode", "");
        if (boundMachine.length() > 0 && !boundMachine.equals(machineCode)) {
            valid = false;
            status.getWarnings().add("授权文件不属于当前机器");
        }
        Long expiresAt = status.getExpiresAt();
        if (expiresAt != null && expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            valid = false;
            status.getWarnings().add("授权已过期");
        }
        if (status.getUsedDevices() > status.getMaxDevices()) {
            valid = false;
            status.getWarnings().add("设备数量超过授权限制");
        }
        if (status.getUsedPoints() > status.getMaxPoints()) {
            valid = false;
            status.getWarnings().add("点位数量超过授权限制");
        }
        status.setValid(valid);
        return status;
    }

    public LicenseStatus activate(String licenseText) {
        if (licenseText == null || licenseText.trim().length() == 0) {
            throw new IllegalArgumentException("license text is required");
        }
        JSONObject parsed = parseLicenseText(licenseText.trim());
        if (!parsed.containsKey("machineCode")) {
            parsed.put("machineCode", machineCode);
        }
        if (!parsed.containsKey("issuedAt")) {
            parsed.put("issuedAt", System.currentTimeMillis());
        }
        File file = new File(licenseFile);
        File parent = file.getParentFile();
        try {
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.write(file.toPath(), JSON.toJSONString(parsed).getBytes(StandardCharsets.UTF_8));
            this.licensePayload = parsed;
            return status();
        } catch (Exception e) {
            throw new IllegalStateException("save license failed: " + e.getMessage(), e);
        }
    }

    public void assertDeviceAllowed() {
        LicenseStatus status = status();
        if (!Boolean.TRUE.equals(status.getValid())) {
            throw new IllegalStateException("license invalid: " + String.join(",", status.getWarnings()));
        }
        if (status.getUsedDevices() >= status.getMaxDevices()) {
            throw new IllegalStateException("device limit exceeded");
        }
    }

    public void assertPointAllowed(int creatingCount) {
        LicenseStatus status = status();
        if (!Boolean.TRUE.equals(status.getValid())) {
            throw new IllegalStateException("license invalid: " + String.join(",", status.getWarnings()));
        }
        int count = Math.max(1, creatingCount);
        if (status.getUsedPoints() + count > status.getMaxPoints()) {
            throw new IllegalStateException("point limit exceeded");
        }
    }

    private void fillDevelopmentLicense(LicenseStatus status) {
        status.setValid(true);
        status.setMode("DEVELOPMENT");
        status.setLicenseNo("DEV-" + machineCode.substring(0, Math.min(8, machineCode.length())));
        status.setIssuedAt(System.currentTimeMillis());
        status.setExpiresAt(null);
        status.setMaxDevices(200);
        status.setMaxPoints(20000);
        status.setEnabledProtocols(Arrays.asList("MODBUS_TCP", "MQTT", "OPC_UA"));
        status.getWarnings().add("当前使用开发授权，正式部署前需要导入授权文件或加密狗授权");
    }

    private void loadLicenseFile() {
        try {
            File file = new File(licenseFile);
            if (!file.exists()) {
                licensePayload = null;
                return;
            }
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            licensePayload = parseLicenseText(text);
        } catch (Exception e) {
            licensePayload = null;
        }
    }

    private JSONObject parseLicenseText(String text) {
        if (text.startsWith("{")) {
            return JSON.parseObject(text);
        }
        byte[] decoded = java.util.Base64.getDecoder().decode(text);
        return JSON.parseObject(new String(decoded, StandardCharsets.UTF_8));
    }

    private String buildMachineCode() {
        String raw = safe(System.getenv("COMPUTERNAME"))
                + "|" + safe(System.getenv("USERNAME"))
                + "|" + safe(System.getProperty("os.name"))
                + "|" + safe(System.getProperty("user.home"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 12 && i < bytes.length; i++) {
                builder.append(String.format("%02X", bytes[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            return String.valueOf(Math.abs(raw.hashCode()));
        }
    }

    private String text(String key, String defaultValue) {
        String value = licensePayload.getString(key);
        return value == null ? defaultValue : value;
    }

    private Long number(String key, Long defaultValue) {
        Long value = licensePayload.getLong(key);
        return value == null ? defaultValue : value;
    }

    private Integer integer(String key, Integer defaultValue) {
        Integer value = licensePayload.getInteger(key);
        return value == null ? defaultValue : value;
    }

    private List<String> protocols() {
        List<String> list = licensePayload.getList("enabledProtocols", String.class);
        return list == null || list.isEmpty() ? Arrays.asList("MODBUS_TCP", "MQTT", "OPC_UA") : list;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
