package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.dto.CurrentUserInfo;
import com.iot.backend.dto.LoginRequest;
import com.iot.backend.dto.LoginResponse;
import com.iot.backend.entity.SysUser;
import com.iot.backend.service.AuthService;
import com.iot.backend.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            return Result.success(authService.login(request.getUsername(), request.getPassword()));
        } catch (IllegalArgumentException ex) {
            return Result.error(401, ex.getMessage());
        }
    }

    @GetMapping("/current")
    public Result<CurrentUserInfo> current(@RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            String token = authorization == null ? null : authorization.replace("Bearer ", "");
            return Result.success(authService.currentUser(token));
        } catch (IllegalArgumentException ex) {
            return Result.error(401, ex.getMessage());
        }
    }

    @GetMapping("/info")
    public Result<SysUser> getUserInfo(@RequestParam(defaultValue = "li_shifu") String username) {
        SysUser user = sysUserService.getUserInfoByUsername(username);
        if (user == null) {
            return Result.error(404, "user not found");
        }
        user.setPassword(null);
        return Result.success(user);
    }
}
