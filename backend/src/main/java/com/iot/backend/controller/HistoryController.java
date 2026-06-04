package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.entity.IotPointHistory;
import com.iot.backend.service.PermissionService;
import com.iot.backend.service.PointHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    @Autowired
    private PointHistoryService pointHistoryService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping("/points")
    public Result<List<IotPointHistory>> pointHistory(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @RequestParam(required = false) Long pointId,
                                                      @RequestParam(required = false) Long deviceId,
                                                      @RequestParam(required = false) Long projectId,
                                                      @RequestParam(required = false) Long groupId,
                                                      @RequestParam(required = false) Long startTime,
                                                      @RequestParam(required = false) Long endTime,
                                                      @RequestParam(required = false) Integer limit) {
        if (pointId == null && deviceId == null && projectId == null) {
            return Result.error(400, "projectId, deviceId or pointId is required");
        }
        if (pointId != null && !permissionService.canViewPoint(authorization, pointId)) {
            return Result.error(403, "current account has no permission to view this point");
        }
        if (pointId == null && deviceId != null && !permissionService.canViewDevice(authorization, deviceId)) {
            return Result.error(403, "current account has no permission to view this device");
        }
        if (pointId == null && deviceId == null && !permissionService.canViewProject(authorization, projectId)) {
            return Result.error(403, "current account has no permission to view this project");
        }
        return Result.success(pointHistoryService.listHistory(pointId, deviceId, projectId, groupId, startTime, endTime, limit));
    }
}
