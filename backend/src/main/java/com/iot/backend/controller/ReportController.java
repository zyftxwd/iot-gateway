package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.dto.CurrentUserInfo;
import com.iot.backend.dto.ReportRequest;
import com.iot.backend.dto.ReportResponse;
import com.iot.backend.dto.ReportSchemeRequest;
import com.iot.backend.dto.ReportTemplate;
import com.iot.backend.entity.IotReportScheme;
import com.iot.backend.service.AuthService;
import com.iot.backend.service.PermissionService;
import com.iot.backend.service.ReportSchemeService;
import com.iot.backend.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ReportSchemeService reportSchemeService;

    @GetMapping("/templates")
    public Result<List<ReportTemplate>> templates() {
        return Result.success(reportService.templates());
    }

    @GetMapping("/schemes")
    public Result<List<IotReportScheme>> schemes(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                 @RequestParam(required = false) String reportType) {
        CurrentUserInfo user = currentUser(authorization);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(reportSchemeService.list(user, reportType));
    }

    @PostMapping("/schemes")
    public Result<IotReportScheme> saveScheme(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestBody ReportSchemeRequest request) {
        CurrentUserInfo user = currentUser(authorization);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        try {
            return Result.success(reportSchemeService.save(user, request));
        } catch (IllegalArgumentException error) {
            return Result.error(400, error.getMessage());
        }
    }

    @DeleteMapping("/schemes/{id}")
    public Result<Boolean> deleteScheme(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable Long id) {
        CurrentUserInfo user = currentUser(authorization);
        if (user == null) {
            return Result.error(401, "请先登录");
        }
        try {
            reportSchemeService.delete(user, id);
            return Result.success(true);
        } catch (IllegalArgumentException error) {
            return Result.error(403, error.getMessage());
        }
    }

    @PostMapping("/preview")
    public Result<ReportResponse> preview(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestBody ReportRequest request) {
        if (!canViewScope(authorization, request)) {
            return Result.error(403, "current account has no permission to view this report scope");
        }
        return Result.success(reportService.preview(request, permissionService.visibleProjectIds(authorization)));
    }

    @PostMapping("/export")
    public void export(@RequestHeader(value = "Authorization", required = false) String authorization,
                       @RequestBody ReportRequest request,
                       HttpServletResponse response) throws Exception {
        if (!canViewScope(authorization, request)) {
            response.setStatus(403);
            return;
        }
        reportService.exportExcel(request, permissionService.visibleProjectIds(authorization), response);
    }

    private boolean canViewScope(String authorization, ReportRequest request) {
        if (request == null) {
            return true;
        }
        if (request.getPointId() != null) {
            return permissionService.canViewPoint(authorization, request.getPointId());
        }
        if (request.getDeviceId() != null) {
            return permissionService.canViewDevice(authorization, request.getDeviceId());
        }
        if (request.getProjectId() != null) {
            return permissionService.canViewProject(authorization, request.getProjectId());
        }
        return permissionService.currentUser(authorization) != null;
    }

    private CurrentUserInfo currentUser(String authorization) {
        try {
            if (authorization == null || authorization.trim().length() == 0) {
                return null;
            }
            String token = authorization.replace("Bearer", "").trim();
            return authService.currentUser(token);
        } catch (Exception error) {
            return null;
        }
    }
}
