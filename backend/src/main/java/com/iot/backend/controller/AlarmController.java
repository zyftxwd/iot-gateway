package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.entity.IotAlarmEvent;
import com.iot.backend.service.AlarmEventService;
import com.iot.backend.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alarms")
public class AlarmController {

    @Autowired
    private AlarmEventService alarmEventService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result<List<IotAlarmEvent>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long projectId,
                                            @RequestParam(required = false) Long deviceId) {
        if (projectId != null && !permissionService.canViewProject(authorization, projectId)) {
            return Result.error(403, "current account has no permission to view this project");
        }
        if (deviceId != null && !permissionService.canViewDevice(authorization, deviceId)) {
            return Result.error(403, "current account has no permission to view this device");
        }
        return Result.success(alarmEventService.list(status, projectId, deviceId, permissionService.visibleProjectIds(authorization)));
    }

    @PostMapping("/{id}/resolve")
    public Result<Boolean> resolve(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @PathVariable Long id) {
        IotAlarmEvent alarm = alarmEventService.get(id);
        if (alarm == null) {
            return Result.error(404, "alarm not found");
        }
        if (!permissionService.canOperateProject(authorization, alarm.getProjectId())) {
            return Result.error(403, "current account has no permission to handle this alarm");
        }
        return Result.success(alarmEventService.resolve(id));
    }
}
