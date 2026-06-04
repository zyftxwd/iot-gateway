package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.entity.IotMaintenanceCard;
import com.iot.backend.entity.IotWorkOrderAttachment;
import com.iot.backend.service.PermissionService;
import com.iot.backend.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maintenance-cards")
public class MaintenanceCardController {

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result<List<IotMaintenanceCard>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @RequestParam(required = false) Long projectId,
                                                 @RequestParam(required = false) Long deviceId) {
        if (projectId != null && !permissionService.canViewProject(authorization, projectId)) {
            return Result.error(403, "current account has no permission to view this project");
        }
        if (deviceId != null && !permissionService.canViewDevice(authorization, deviceId)) {
            return Result.error(403, "current account has no permission to view this device");
        }
        return Result.success(workOrderService.listMaintenanceCards(projectId, deviceId, permissionService.visibleProjectIds(authorization)));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @PathVariable Long id) {
        IotMaintenanceCard card = workOrderService.getMaintenanceCard(id);
        if (card == null) {
            return Result.error(404, "maintenance card not found");
        }
        if (card.getProjectId() != null && !permissionService.canViewProject(authorization, card.getProjectId())) {
            return Result.error(403, "current account has no permission to view this maintenance card");
        }
        List<IotWorkOrderAttachment> attachments = workOrderService.listAttachments(card.getWorkOrderId());
        Map<String, Object> result = new HashMap<>();
        result.put("card", card);
        result.put("attachments", attachments);
        return Result.success(result);
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @PathVariable Long id) {
        IotMaintenanceCard card = workOrderService.getMaintenanceCard(id);
        if (card == null) {
            return Result.error(404, "maintenance card not found");
        }
        if (card.getProjectId() != null && !permissionService.canManageProject(authorization, card.getProjectId())) {
            return Result.error(403, "current account has no permission to delete this maintenance card");
        }
        return Result.success(workOrderService.deleteMaintenanceCard(id));
    }
}
