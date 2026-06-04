package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.dto.LicenseActivationRequest;
import com.iot.backend.dto.LicenseStatus;
import com.iot.backend.service.LicenseService;
import com.iot.backend.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/license")
public class LicenseController {

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping("/status")
    public Result<LicenseStatus> status(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!permissionService.isAdmin(authorization)) {
            return Result.error(403, "only admin can view license status");
        }
        return Result.success(licenseService.status());
    }

    @PostMapping("/activate")
    public Result<LicenseStatus> activate(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestBody LicenseActivationRequest request) {
        if (!permissionService.isAdmin(authorization)) {
            return Result.error(403, "only admin can activate license");
        }
        return Result.success(licenseService.activate(request.getLicenseText()));
    }
}
