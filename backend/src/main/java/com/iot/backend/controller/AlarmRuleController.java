package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.entity.IotAlarmRule;
import com.iot.backend.service.AlarmRuleService;
import com.iot.backend.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alarm-rules")
public class AlarmRuleController {

    @Autowired
    private AlarmRuleService ruleService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result<List<IotAlarmRule>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestParam(required = false) Long pointId,
                                           @RequestParam(required = false) Long deviceId,
                                           @RequestParam(required = false) Long projectId) {
        if (pointId != null && !permissionService.canViewPoint(authorization, pointId)) {
            return Result.error(403, "current account has no permission to view this point");
        }
        if (pointId == null && deviceId != null && !permissionService.canViewDevice(authorization, deviceId)) {
            return Result.error(403, "current account has no permission to view this device");
        }
        if (pointId == null && deviceId == null && projectId != null && !permissionService.canViewProject(authorization, projectId)) {
            return Result.error(403, "current account has no permission to view this project");
        }
        return Result.success(ruleService.list(pointId, deviceId, projectId));
    }

    @PostMapping
    public Result<IotAlarmRule> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody IotAlarmRule rule) {
        if (!permissionService.canManagePoint(authorization, rule.getPointId())) {
            return Result.error(403, "current account has no permission to manage this point");
        }
        return Result.success(ruleService.create(rule));
    }

    @PutMapping("/{id}")
    public Result<IotAlarmRule> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable Long id,
                                       @RequestBody IotAlarmRule rule) {
        IotAlarmRule existing = ruleService.get(id);
        if (existing == null) {
            return Result.error(404, "alarm rule not found");
        }
        Long pointId = rule.getPointId() == null ? existing.getPointId() : rule.getPointId();
        if (!permissionService.canManagePoint(authorization, pointId)) {
            return Result.error(403, "current account has no permission to manage this point");
        }
        rule.setPointId(pointId);
        return Result.success(ruleService.update(id, rule));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @PathVariable Long id) {
        IotAlarmRule existing = ruleService.get(id);
        if (existing == null) {
            return Result.error(404, "alarm rule not found");
        }
        if (!permissionService.canManagePoint(authorization, existing.getPointId())) {
            return Result.error(403, "current account has no permission to manage this point");
        }
        return Result.success(ruleService.delete(id));
    }
}
