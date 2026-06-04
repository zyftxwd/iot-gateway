package com.iot.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class CurrentUserInfo {
    private Long userId;
    private String username;
    private String nickName;
    private Long deptId;
    private String roleKey;
    private List<ProjectPermissionInfo> projectPermissions = new ArrayList<>();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    public List<ProjectPermissionInfo> getProjectPermissions() {
        return projectPermissions;
    }

    public void setProjectPermissions(List<ProjectPermissionInfo> projectPermissions) {
        this.projectPermissions = projectPermissions;
    }
}
