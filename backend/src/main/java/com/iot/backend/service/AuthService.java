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
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
public class AuthService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    public LoginResponse login(String username, String password) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        if ("DISABLED".equalsIgnoreCase(user.getStatus())) {
            throw new IllegalArgumentException("user disabled");
        }
        if (password != null && user.getPassword() != null && !user.getPassword().equals(password)) {
            throw new IllegalArgumentException("invalid password");
        }

        LoginResponse response = new LoginResponse();
        response.setToken(encodeToken(user));
        response.setUser(toCurrentUser(user));
        return response;
    }

    public CurrentUserInfo currentUser(String token) {
        Long userId = decodeUserId(token);
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

    private String encodeToken(SysUser user) {
        String raw = user.getUserId() + ":" + user.getUsername() + ":" + System.currentTimeMillis();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Long decodeUserId(String token) {
        if (token == null || token.trim().length() == 0) {
            throw new IllegalArgumentException("missing token");
        }
        String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        return Long.parseLong(raw.split(":")[0]);
    }
}
