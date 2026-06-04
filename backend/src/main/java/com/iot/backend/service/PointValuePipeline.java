package com.iot.backend.service;

import com.alibaba.fastjson2.JSON;
import com.iot.backend.dto.DeviceCollectSnapshot;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.websocket.WebSocketServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PointValuePipeline {

    @Autowired
    private PointRuntimeDataService runtimeDataService;

    @Autowired
    private PointHistoryService pointHistoryService;

    @Autowired
    private AlarmEvaluateService alarmEvaluateService;

    public void handle(DeviceCollectSnapshot snapshot) {
        if (snapshot == null || snapshot.getDevice() == null || snapshot.getValues() == null) {
            return;
        }

        IotCommDevice device = snapshot.getDevice();
        Map<String, Object> values = snapshot.getValues();

        try {
            runtimeDataService.updateDeviceValues(device.getId(), values);
        } catch (Exception ex) {
            System.err.println("runtime update failed, deviceId=" + device.getId() + ", message=" + ex.getMessage());
        }
        if (snapshot.isStoreHistory()) {
            try {
                pointHistoryService.saveDeviceSnapshot(device, snapshot.getPoints(), values);
            } catch (Exception ex) {
                System.err.println("history save failed, deviceId=" + device.getId() + ", message=" + ex.getMessage());
            }
        }
        try {
            alarmEvaluateService.evaluate(snapshot);
        } catch (Exception ex) {
            System.err.println("alarm evaluate failed, deviceId=" + device.getId() + ", message=" + ex.getMessage());
        }
        try {
            WebSocketServer.sendInfo(JSON.toJSONString(values));
        } catch (Exception ex) {
            System.err.println("websocket push failed, deviceId=" + device.getId() + ", message=" + ex.getMessage());
        }
    }
}
