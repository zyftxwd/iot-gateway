package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.task.GatewayEngineTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/collector")
public class CollectorController {

    @Autowired
    private GatewayEngineTask gatewayEngineTask;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(gatewayEngineTask.getCollectorStatus());
    }
}
