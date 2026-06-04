package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.dto.CurrentUserInfo;
import com.iot.backend.dto.WorkOrderActionRequest;
import com.iot.backend.dto.WorkOrderCreateRequest;
import com.iot.backend.entity.IotAlarmEvent;
import com.iot.backend.entity.IotWorkOrder;
import com.iot.backend.entity.IotWorkOrderAttachment;
import com.iot.backend.entity.IotWorkOrderPolicy;
import com.iot.backend.entity.SysUser;
import com.iot.backend.service.AlarmEventService;
import com.iot.backend.service.PermissionService;
import com.iot.backend.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private AlarmEventService alarmEventService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result<List<IotWorkOrder>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Long projectId,
                                           @RequestParam(required = false) Long deviceId) {
        if (projectId != null && !permissionService.canViewProject(authorization, projectId)) {
            return Result.error(403, "current account has no permission to view this project");
        }
        if (deviceId != null && !permissionService.canViewDevice(authorization, deviceId)) {
            return Result.error(403, "current account has no permission to view this device");
        }
        return Result.success(workOrderService.list(status, projectId, deviceId, permissionService.visibleProjectIds(authorization)));
    }

    @GetMapping("/candidates")
    public Result<Map<String, List<SysUser>>> candidates(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                         @RequestParam Long projectId) {
        try {
            CurrentUserInfo operator = permissionService.currentUser(authorization);
            return Result.success(workOrderService.candidates(projectId, operator));
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @GetMapping("/policies/{projectId}")
    public Result<IotWorkOrderPolicy> policy(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @PathVariable Long projectId) {
        try {
            CurrentUserInfo operator = permissionService.currentUser(authorization);
            return Result.success(workOrderService.getPolicy(projectId, operator));
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id) {
        IotWorkOrder order = workOrderService.get(id);
        if (order == null) {
            return Result.error(404, "work order not found");
        }
        if (order.getProjectId() != null && !permissionService.canViewProject(authorization, order.getProjectId())) {
            return Result.error(403, "current account has no permission to view this work order");
        }
        return Result.success(workOrderService.detail(id));
    }

    @PostMapping("/policies/{projectId}")
    public Result<IotWorkOrderPolicy> savePolicy(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @PathVariable Long projectId,
                                                 @RequestBody IotWorkOrderPolicy request) {
        try {
            CurrentUserInfo operator = permissionService.currentUser(authorization);
            return Result.success(workOrderService.savePolicy(projectId, request, operator));
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @PostMapping
    public Result<IotWorkOrder> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @RequestBody WorkOrderCreateRequest request) {
        try {
            CurrentUserInfo operator = permissionService.currentUser(authorization);
            Long projectId = workOrderService.resolveProjectId(request);
            request.setProjectId(projectId);
            return Result.success(workOrderService.createManual(request, operator));
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @PostMapping("/from-alarm/{alarmId}")
    public Result<IotWorkOrder> createFromAlarm(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @PathVariable Long alarmId) {
        IotAlarmEvent alarm = alarmEventService.get(alarmId);
        if (alarm == null) {
            return Result.error(404, "alarm not found");
        }
        try {
            CurrentUserInfo operator = permissionService.currentUser(authorization);
            return Result.success(workOrderService.createFromAlarm(alarm, operator));
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @PostMapping("/{id}/action")
    public Result<IotWorkOrder> action(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable Long id,
                                       @RequestBody WorkOrderActionRequest request) {
        IotWorkOrder order = workOrderService.get(id);
        if (order == null) {
            return Result.error(404, "work order not found");
        }
        try {
            CurrentUserInfo operator = permissionService.currentUser(authorization);
            return Result.success(workOrderService.applyAction(id, request, operator));
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @PostMapping("/{id}/attachments")
    public Result<IotWorkOrderAttachment> uploadAttachment(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                           @PathVariable Long id,
                                                           @RequestParam("file") MultipartFile file) {
        try {
            CurrentUserInfo operator = permissionService.currentUser(authorization);
            return Result.success(workOrderService.uploadAttachment(id, file, operator));
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        } catch (IOException ex) {
            return Result.error(500, "附件保存失败");
        }
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @PathVariable Long id) {
        IotWorkOrder order = workOrderService.get(id);
        if (order == null) {
            return Result.error(404, "work order not found");
        }
        if (order.getProjectId() != null && !permissionService.canManageProject(authorization, order.getProjectId())) {
            return Result.error(403, "current account has no permission to delete this work order");
        }
        return Result.success(workOrderService.deleteWorkOrder(id));
    }
}
