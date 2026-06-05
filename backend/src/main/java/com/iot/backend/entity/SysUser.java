package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data // Lombok 注解，自动生成 get/set 方法
@TableName("sys_user") // 告诉 MyBatis-Plus 这个类对应哪张表
public class SysUser {

    @TableId(type = IdType.AUTO) // 主键自增
    private Long userId;

    private String username;

    private String nickName;

    private String password;

    private Long deptId;

    private String roleKey;

    private String status;

    private Integer failedLoginCount;

    private Long lockUntil;

    private Long lastLoginTime;

    private Long passwordChangedTime;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(Integer failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public Long getLockUntil() {
        return lockUntil;
    }

    public void setLockUntil(Long lockUntil) {
        this.lockUntil = lockUntil;
    }

    public Long getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(Long lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public Long getPasswordChangedTime() {
        return passwordChangedTime;
    }

    public void setPasswordChangedTime(Long passwordChangedTime) {
        this.passwordChangedTime = passwordChangedTime;
    }
}
