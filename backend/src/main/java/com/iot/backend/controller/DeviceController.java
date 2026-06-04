package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.dto.DeviceFullConfig;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.service.IotCommDeviceService;
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

/**
 * 设备管理接口。
 * 负责设备新增、批量新增、查询、修改、删除，以及“设备 + 点表”的完整配置导入。
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    @Autowired
    private IotCommDeviceService deviceService;

    @Autowired
    private PermissionService permissionService;

    /**
     * 查询设备列表。
     * keyword 按设备名称或 IP 模糊搜索；protocolType 按协议类型筛选。
     */
    @GetMapping
    public Result<List<IotCommDevice>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String protocolType,
                                            @RequestParam(required = false) Long projectId,
                                            @RequestParam(required = false) Long groupId) {
        return Result.success(deviceService.listDevices(keyword, protocolType, projectId, groupId, permissionService.visibleProjectIds(authorization)));
    }

    /**
     * 查询单台设备基础信息。
     */
    @GetMapping("/{id}")
    public Result<IotCommDevice> get(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @PathVariable Long id) {
        if (!permissionService.canViewDevice(authorization, id)) {
            return Result.error(403, "current account has no permission to view this device");
        }
        IotCommDevice device = deviceService.getDevice(id);
        if (device == null) {
            return Result.error(404, "device not found");
        }
        return Result.success(device);
    }

    /**
     * 查询设备完整配置：设备基础信息 + 该设备下的全部点表。
     */
    @GetMapping("/{id}/full-config")
    public Result<DeviceFullConfig> getFullConfig(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @PathVariable Long id) {
        if (!permissionService.canViewDevice(authorization, id)) {
            return Result.error(403, "current account has no permission to view this device");
        }
        DeviceFullConfig config = deviceService.getFullConfig(id);
        if (config == null) {
            return Result.error(404, "device not found");
        }
        return Result.success(config);
    }

    /**
     * 新增单台设备。
     */
    @PostMapping
    public Result<IotCommDevice> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestBody IotCommDevice device) {
        if (!permissionService.canManageProject(authorization, device.getProjectId())) {
            return Result.error(403, "当前账号没有该项目的设备管理权限");
        }
        return Result.success(deviceService.createDevice(device));
    }

    /**
     * 批量新增设备，只保存设备基础信息，不包含点表。
     */
    @PostMapping("/batch")
    public Result<List<IotCommDevice>> createBatch(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @RequestBody List<IotCommDevice> devices) {
        if (!permissionService.isAdmin(authorization)) {
            return Result.error(403, "批量新增设备暂时只允许系统管理员操作");
        }
        return Result.success(deviceService.createDevices(devices));
    }

    /**
     * 批量导入完整设备配置。
     * 一个请求里可以带多台设备，每台设备下面可以带自己的点表。
     */
    @PostMapping("/full-config/batch")
    public Result<List<DeviceFullConfig>> createFullConfigBatch(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                @RequestBody List<DeviceFullConfig> configs) {
        if (!permissionService.isAdmin(authorization)) {
            return Result.error(403, "完整配置导入暂时只允许系统管理员操作");
        }
        return Result.success(deviceService.createFullConfigs(configs));
    }

    /**
     * 修改设备基础信息。
     */
    @PutMapping("/{id}")
    public Result<IotCommDevice> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable Long id,
                                        @RequestBody IotCommDevice device) {
        if (!permissionService.canManageDevice(authorization, id)) {
            return Result.error(403, "当前账号没有该设备的管理权限");
        }
        return Result.success(deviceService.updateDevice(id, device));
    }

    /**
     * 删除设备，同时删除该设备下的点表，避免留下无主点位。
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @PathVariable Long id) {
        if (!permissionService.canManageDevice(authorization, id)) {
            return Result.error(403, "当前账号没有该设备的管理权限");
        }
        return Result.success(deviceService.deleteDevice(id));
    }
}
