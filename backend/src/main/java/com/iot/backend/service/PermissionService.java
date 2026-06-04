package com.iot.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.backend.dto.CurrentUserInfo;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotCommPoint;
import com.iot.backend.entity.IotProjectGroup;
import com.iot.backend.entity.SysUserProject;
import com.iot.backend.mapper.IotCommDeviceMapper;
import com.iot.backend.mapper.IotCommPointMapper;
import com.iot.backend.mapper.IotProjectGroupMapper;
import com.iot.backend.mapper.SysUserProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PermissionService {

    @Autowired
    private AuthService authService;

    @Autowired
    private IotCommDeviceMapper deviceMapper;

    @Autowired
    private IotCommPointMapper pointMapper;

    @Autowired
    private IotProjectGroupMapper groupMapper;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    public CurrentUserInfo currentUser(String authorization) {
        try {
            String token = authorization == null ? null : authorization.replace("Bearer ", "");
            return authService.currentUser(token);
        } catch (Exception ex) {
            return null;
        }
    }

    public boolean isAdmin(String authorization) {
        CurrentUserInfo user = currentUser(authorization);
        return user != null && "admin".equalsIgnoreCase(user.getRoleKey());
    }

    public boolean canOperateProject(String authorization, Long projectId) {
        return hasProjectPermission(authorization, projectId, "OPERATE", "ADMIN");
    }

    public boolean canViewProject(String authorization, Long projectId) {
        return hasProjectPermission(authorization, projectId, "VIEW", "OPERATE", "ADMIN");
    }

    public List<Long> visibleProjectIds(String authorization) {
        CurrentUserInfo user = currentUser(authorization);
        if (user == null) {
            return Collections.emptyList();
        }
        if ("admin".equalsIgnoreCase(user.getRoleKey())) {
            return null;
        }

        List<SysUserProject> permissions = userProjectMapper.selectList(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getUserId, user.getUserId())
                .in(SysUserProject::getPermissionLevel, "VIEW", "OPERATE", "ADMIN"));
        List<Long> ids = new ArrayList<>();
        for (SysUserProject permission : permissions) {
            ids.add(permission.getProjectId());
        }
        return ids;
    }

    public boolean canManageProject(String authorization, Long projectId) {
        return hasProjectPermission(authorization, projectId, "ADMIN");
    }

    public boolean canManageDevice(String authorization, Long deviceId) {
        IotCommDevice device = deviceMapper.selectById(deviceId);
        return device != null && canManageProject(authorization, device.getProjectId());
    }

    public boolean canViewDevice(String authorization, Long deviceId) {
        IotCommDevice device = deviceMapper.selectById(deviceId);
        return device != null && canViewProject(authorization, device.getProjectId());
    }

    public boolean canViewPoint(String authorization, Long pointId) {
        IotCommPoint point = pointMapper.selectById(pointId);
        return point != null && canViewDevice(authorization, point.getCommDeviceId());
    }

    public boolean canManagePoint(String authorization, Long pointId) {
        IotCommPoint point = pointMapper.selectById(pointId);
        return point != null && canManageDevice(authorization, point.getCommDeviceId());
    }

    public boolean canManageGroup(String authorization, Long groupId) {
        IotProjectGroup group = groupMapper.selectById(groupId);
        return group != null && canManageProject(authorization, group.getProjectId());
    }

    private boolean hasProjectPermission(String authorization, Long projectId, String... allowedLevels) {
        try {
            CurrentUserInfo user = currentUser(authorization);
            if (user == null) {
                return false;
            }
            if ("admin".equalsIgnoreCase(user.getRoleKey())) {
                return true;
            }
            if (projectId == null) {
                return false;
            }
            SysUserProject permission = userProjectMapper.selectOne(new LambdaQueryWrapper<SysUserProject>()
                    .eq(SysUserProject::getUserId, user.getUserId())
                    .eq(SysUserProject::getProjectId, projectId)
                    .last("LIMIT 1"));
            if (permission == null) {
                return false;
            }
            for (String level : allowedLevels) {
                if (level.equalsIgnoreCase(permission.getPermissionLevel())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean canOperateDevice(String authorization, Long deviceId) {
        IotCommDevice device = deviceMapper.selectById(deviceId);
        return device != null && canOperateProject(authorization, device.getProjectId());
    }

    public boolean canOperatePoint(String authorization, Long pointId) {
        IotCommPoint point = pointMapper.selectById(pointId);
        return point != null && canOperateDevice(authorization, point.getCommDeviceId());
    }
}
