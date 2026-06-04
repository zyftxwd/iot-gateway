package com.iot.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.iot.backend.common.Result;
import com.iot.backend.dto.CurrentUserInfo;
import com.iot.backend.dto.UserCreateRequest;
import com.iot.backend.dto.UserDeptSaveRequest;
import com.iot.backend.dto.UserProjectPermissionSaveRequest;
import com.iot.backend.dto.UserRoleSaveRequest;
import com.iot.backend.entity.SysDept;
import com.iot.backend.entity.SysRole;
import com.iot.backend.entity.SysUser;
import com.iot.backend.entity.SysUserProject;
import com.iot.backend.mapper.SysDeptMapper;
import com.iot.backend.mapper.SysRoleMapper;
import com.iot.backend.mapper.SysUserMapper;
import com.iot.backend.mapper.SysUserProjectMapper;
import com.iot.backend.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth-admin")
public class AuthAdminController {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysDeptMapper deptMapper;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    @Autowired
    private AuthService authService;

    @GetMapping("/users")
    public Result<List<SysUser>> users(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Result<Boolean> adminCheck = requireAdmin(authorization);
        if (adminCheck.getCode() != 200) {
            return Result.error(adminCheck.getCode(), adminCheck.getMsg());
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getUserId));
        for (SysUser user : users) {
            user.setPassword(null);
        }
        return Result.success(users);
    }


    @GetMapping("/roles")
    public Result<List<SysRole>> roles() {
        return Result.success(roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getRoleId)));
    }

    @GetMapping("/depts")
    public Result<List<SysDept>> depts() {
        return Result.success(deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getStatus, "ACTIVE")
                .orderByAsc(SysDept::getSortNo)
                .orderByAsc(SysDept::getDeptId)));
    }

    @PostMapping("/users")
    public Result<SysUser> createUser(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @RequestBody UserCreateRequest request) {
        Result<Boolean> adminCheck = requireAdmin(authorization);
        if (adminCheck.getCode() != 200) {
            return Result.error(adminCheck.getCode(), adminCheck.getMsg());
        }
        if (request.getUsername() == null || request.getUsername().trim().length() == 0) {
            return Result.error(400, "账号不能为空");
        }
        SysUser existing = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
                .last("LIMIT 1"));
        if (existing != null) {
            return Result.error(400, "账号已存在");
        }
        String roleKey = request.getRoleKey() == null ? "viewer" : request.getRoleKey();
        if (!roleExists(roleKey)) {
            return Result.error(400, "角色不存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setNickName(request.getNickName());
        user.setPassword(request.getPassword() == null || request.getPassword().length() == 0 ? "123456" : request.getPassword());
        user.setDeptId(request.getDeptId());
        user.setRoleKey(roleKey);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        user.setPassword(null);
        return Result.success(user);
    }

    @GetMapping("/users/{userId}/projects")
    public Result<List<SysUserProject>> userProjects(@PathVariable Long userId) {
        return Result.success(userProjectMapper.selectList(
                new LambdaQueryWrapper<SysUserProject>().eq(SysUserProject::getUserId, userId)
        ));
    }

    @PostMapping("/users/{userId}/projects")
    public Result<Boolean> saveUserProject(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @PathVariable Long userId,
                                           @RequestBody UserProjectPermissionSaveRequest request) {
        CurrentUserInfo operator = currentAdmin(authorization);
        if (operator == null) {
            return Result.error(403, "只有系统管理员可以分配项目权限");
        }
        if (operator.getUserId().equals(userId)) {
            return Result.error(400, "系统管理员默认拥有全部项目权限，不需要给自己分配项目权限");
        }
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            return Result.error(404, "用户不存在");
        }
        if ("admin".equalsIgnoreCase(target.getRoleKey())) {
            return Result.error(400, "系统管理员默认拥有全部项目权限，不需要项目授权");
        }
        if ("NONE".equalsIgnoreCase(request.getPermissionLevel())) {
            userProjectMapper.delete(new LambdaQueryWrapper<SysUserProject>()
                    .eq(SysUserProject::getUserId, userId)
                    .eq(SysUserProject::getProjectId, request.getProjectId()));
            return Result.success(true);
        }

        SysUserProject existing = userProjectMapper.selectOne(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getUserId, userId)
                .eq(SysUserProject::getProjectId, request.getProjectId())
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new SysUserProject();
            existing.setUserId(userId);
            existing.setProjectId(request.getProjectId());
            existing.setPermissionLevel(request.getPermissionLevel());
            userProjectMapper.insert(existing);
        } else {
            existing.setPermissionLevel(request.getPermissionLevel());
            userProjectMapper.updateById(existing);
        }
        return Result.success(true);
    }

    @PostMapping("/users/{userId}/role")
    public Result<Boolean> saveUserRole(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable Long userId,
                                        @RequestBody UserRoleSaveRequest request) {
        CurrentUserInfo operator = currentAdmin(authorization);
        if (operator == null) {
            return Result.error(403, "只有系统管理员可以分配总角色");
        }
        if (operator.getUserId().equals(userId)) {
            return Result.error(400, "不能修改自己的总角色");
        }
        if (!roleExists(request.getRoleKey())) {
            return Result.error(400, "角色不存在");
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        user.setRoleKey(request.getRoleKey());
        userMapper.updateById(user);
        return Result.success(true);
    }

    @PostMapping("/users/{userId}/dept")
    public Result<Boolean> saveUserDept(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable Long userId,
                                        @RequestBody UserDeptSaveRequest request) {
        CurrentUserInfo operator = currentAdmin(authorization);
        if (operator == null) {
            return Result.error(403, "只有系统管理员可以分配部门");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        user.setDeptId(request.getDeptId());
        userMapper.updateById(user);
        return Result.success(true);
    }

    @PostMapping("/users/{userId}/status")
    public Result<Boolean> saveUserStatus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @PathVariable Long userId,
                                          @RequestBody Map<String, String> request) {
        CurrentUserInfo operator = currentAdmin(authorization);
        if (operator == null) {
            return Result.error(403, "只有系统管理员可以维护账号状态");
        }
        if (operator.getUserId().equals(userId)) {
            return Result.error(400, "不能禁用或启用当前登录账号");
        }
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            return Result.error(404, "账号不存在");
        }

        String nextStatus = request == null ? "ACTIVE" : request.get("status");
        nextStatus = "DISABLED".equalsIgnoreCase(nextStatus) ? "DISABLED" : "ACTIVE";
        if ("DISABLED".equals(nextStatus) && "admin".equalsIgnoreCase(target.getRoleKey())) {
            long activeAdminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRoleKey, "admin")
                    .ne(SysUser::getStatus, "DISABLED"));
            if (activeAdminCount <= 1) {
                return Result.error(400, "不能禁用最后一个系统管理员账号");
            }
        }

        target.setStatus(nextStatus);
        return Result.success(userMapper.updateById(target) > 0);
    }

    @PostMapping("/users/{userId}/password")
    public Result<Boolean> resetUserPassword(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @PathVariable Long userId,
                                             @RequestBody Map<String, String> request) {
        CurrentUserInfo operator = currentAdmin(authorization);
        if (operator == null) {
            return Result.error(403, "只有系统管理员可以重置账号密码");
        }
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            return Result.error(404, "账号不存在");
        }
        String password = request == null ? null : request.get("password");
        if (password == null || password.trim().length() < 4) {
            return Result.error(400, "新密码至少需要 4 位");
        }
        target.setPassword(password.trim());
        return Result.success(userMapper.updateById(target) > 0);
    }

    @DeleteMapping("/users/{userId}")
    public Result<Boolean> deleteUser(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @PathVariable Long userId) {
        CurrentUserInfo operator = currentAdmin(authorization);
        if (operator == null) {
            return Result.error(403, "只有系统管理员可以删除账号");
        }
        if (operator.getUserId().equals(userId)) {
            return Result.error(400, "不能删除当前登录账号");
        }
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            return Result.error(404, "账号不存在");
        }
        if ("admin".equalsIgnoreCase(target.getRoleKey())) {
            long adminCount = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getRoleKey, "admin"));
            if (adminCount <= 1) {
                return Result.error(400, "不能删除最后一个系统管理员账号");
            }
        }
        userProjectMapper.delete(new LambdaQueryWrapper<SysUserProject>()
                .eq(SysUserProject::getUserId, userId));
        return Result.success(userMapper.deleteById(userId) > 0);
    }

    private boolean roleExists(String roleKey) {
        if (roleKey == null) {
            return false;
        }
        return roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, roleKey)) > 0;
    }

    private Result<Boolean> requireAdmin(String authorization) {
        return currentAdmin(authorization) == null
                ? Result.error(403, "只有系统管理员可以访问权限管理")
                : Result.success(true);
    }

    private CurrentUserInfo currentAdmin(String authorization) {
        try {
            String token = authorization == null ? null : authorization.replace("Bearer ", "");
            CurrentUserInfo currentUser = authService.currentUser(token);
            return "admin".equalsIgnoreCase(currentUser.getRoleKey()) ? currentUser : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
