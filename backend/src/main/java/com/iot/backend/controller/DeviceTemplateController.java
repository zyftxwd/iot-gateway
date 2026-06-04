package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.dto.DeviceFullConfig;
import com.iot.backend.dto.DeviceTemplate;
import com.iot.backend.dto.DeviceTemplateApplyRequest;
import com.iot.backend.service.DeviceTemplateService;
import com.iot.backend.service.PermissionService;
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

import java.util.List;

@RestController
@RequestMapping("/api/device-templates")
public class DeviceTemplateController {

    @Autowired
    private DeviceTemplateService templateService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result<List<DeviceTemplate>> list(@RequestParam(required = false) String protocolType) {
        return Result.success(templateService.listTemplates(protocolType));
    }

    @GetMapping("/{templateKey}")
    public Result<DeviceTemplate> get(@PathVariable String templateKey) {
        DeviceTemplate template = templateService.getTemplate(templateKey);
        if (template == null) {
            return Result.error(404, "template not found");
        }
        return Result.success(template);
    }

    @DeleteMapping("/{templateKey}")
    public Result<Boolean> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @PathVariable String templateKey) {
        if (!permissionService.isAdmin(authorization)) {
            return Result.error(403, "只有系统管理员可以删除设备模板");
        }
        boolean deleted = templateService.deleteTemplate(templateKey);
        if (!deleted) {
            return Result.error(404, "template not found");
        }
        return Result.success(true);
    }

    @PostMapping("/{templateKey}/apply")
    public Result<DeviceFullConfig> apply(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @PathVariable String templateKey,
                                          @RequestBody DeviceTemplateApplyRequest request) {
        if (!permissionService.canManageProject(authorization, request.getProjectId())) {
            return Result.error(403, "当前账号没有该项目的设备管理权限");
        }
        return Result.success(templateService.applyTemplate(templateKey, request));
    }
}
