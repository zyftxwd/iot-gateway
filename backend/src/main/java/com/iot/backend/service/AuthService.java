package com.iot.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.backend.dto.CurrentUserInfo;
import com.iot.backend.dto.LoginResponse;
import com.iot.backend.dto.ProjectPermissionInfo;
import com.iot.backend.entity.SysUser;
import com.iot.backend.entity.SysUserProject;
import com.iot.backend.mapper.SysUserMapper;
import com.iot.backend.mapper.SysUserProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private AuthTokenService authTokenService;

    @Value("${industrial.auth.max-login-failures:5}")
    private int maxLoginFailures;

    @Value("${industrial.auth.lock-minutes:10}")
    private long lockMinutes;

    public LoginResponse login(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username.trim()));
        if (user == null) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        if ("DISABLED".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("user disabled");
        }
        long now = System.currentTimeMillis();
        if (user.getLockUntil() != null && user.getLockUntil() > now) {
            throw new IllegalArgumentException("账号已临时锁定，请稍后再试");
        }
        if (!passwordService.matches(password, user.getPassword())) {
            recordLoginFailure(user, now);
            throw new IllegalArgumentException("账号或密码错误");
        }

        boolean passwordUpgraded = false;
        if (passwordService.needsRehash(user.getPassword())) {
            user.setPassword(passwordService.hash(password));
            user.setPasswordChangedTime(now);
            passwordUpgraded = true;
        }
        recordLoginSuccess(user, now, passwordUpgraded);

        AuthTokenService.IssuedToken issuedToken = authTokenService.issue(user);
        LoginResponse response = new LoginResponse();
        response.setToken(issuedToken.getToken());
        response.setExpiresAt(issuedToken.getExpiresAt());
        response.setUser(toCurrentUser(user));
        return response;
    }

    public CurrentUserInfo currentUser(String token) {
        Long userId = authTokenService.parseUserId(token);
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("invalid token");
        }
        if ("DISABLED".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("user disabled");
        }
        return toCurrentUser(user);
    }

    private CurrentUserInfo toCurrentUser(SysUser user) {
        CurrentUserInfo info = new CurrentUserInfo();
        info.setUserId(user.getUserId());
        info.setUsername(user.getUsername());
        info.setNickName(user.getNickName());
        info.setDeptId(user.getDeptId());
        info.setRoleKey(user.getRoleKey());

        List<SysUserProject> permissions = userProjectMapper.selectList(
                new LambdaQueryWrapper<SysUserProject>().eq(SysUserProject::getUserId, user.getUserId())
        );
        for (SysUserProject permission : permissions) {
            ProjectPermissionInfo item = new ProjectPermissionInfo();
            item.setProjectId(permission.getProjectId());
            item.setPermissionLevel(permission.getPermissionLevel());
            info.getProjectPermissions().add(item);
        }
        return info;
    }

    private void recordLoginFailure(SysUser user, long now) {
        int failedCount = user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount();
        failedCount += 1;
        user.setFailedLoginCount(failedCount);
        if (failedCount >= Math.max(1, maxLoginFailures)) {
            user.setLockUntil(now + Math.max(1, lockMinutes) * 60L * 1000L);
        }
        userMapper.updateById(user);
    }

    private void recordLoginSuccess(SysUser user, long now, boolean passwordUpgraded) {
        user.setFailedLoginCount(0);
        user.setLockUntil(null);
        user.setLastLoginTime(now);
        if (passwordUpgraded && user.getPasswordChangedTime() == null) {
            user.setPasswordChangedTime(now);
        }
        userMapper.updateById(user);
    }
}
