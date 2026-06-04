package com.iot.backend.controller;

import com.iot.backend.common.Result;
import com.iot.backend.dto.ProjectTreeNode;
import com.iot.backend.entity.IotProject;
import com.iot.backend.entity.IotProjectGroup;
import com.iot.backend.service.IotProjectService;
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

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private IotProjectService projectService;

    @Autowired
    private PermissionService permissionService;

    @GetMapping
    public Result<List<IotProject>> listProjects(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(projectService.listProjects(permissionService.visibleProjectIds(authorization)));
    }

    @PostMapping
    public Result<IotProject> createProject(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @RequestBody IotProject project) {
        if (!permissionService.isAdmin(authorization)) {
            return Result.error(403, "只有系统管理员可以新增项目");
        }
        return Result.success(projectService.createProject(project));
    }

    @PutMapping("/{id}")
    public Result<IotProject> updateProject(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @PathVariable Long id,
                                            @RequestBody IotProject project) {
        if (!permissionService.canManageProject(authorization, id)) {
            return Result.error(403, "当前账号没有该项目的管理权限");
        }
        return Result.success(projectService.updateProject(id, project));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> deleteProject(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable Long id) {
        if (!permissionService.canManageProject(authorization, id)) {
            return Result.error(403, "当前账号没有该项目的管理权限");
        }
        return Result.success(projectService.deleteProject(id));
    }

    @GetMapping("/groups")
    public Result<List<IotProjectGroup>> listGroups(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @RequestParam(required = false) Long projectId) {
        return Result.success(projectService.listGroups(projectId, permissionService.visibleProjectIds(authorization)));
    }

    @PostMapping("/groups")
    public Result<IotProjectGroup> createGroup(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @RequestBody IotProjectGroup group) {
        if (!permissionService.canManageProject(authorization, group.getProjectId())) {
            return Result.error(403, "当前账号没有该项目的分组管理权限");
        }
        return Result.success(projectService.createGroup(group));
    }

    @PutMapping("/groups/{id}")
    public Result<IotProjectGroup> updateGroup(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @PathVariable Long id,
                                               @RequestBody IotProjectGroup group) {
        if (!permissionService.canManageGroup(authorization, id)) {
            return Result.error(403, "当前账号没有该分组的管理权限");
        }
        return Result.success(projectService.updateGroup(id, group));
    }

    @DeleteMapping("/groups/{id}")
    public Result<Boolean> deleteGroup(@RequestHeader(value = "Authorization", required = false) String authorization,
                                       @PathVariable Long id) {
        if (!permissionService.canManageGroup(authorization, id)) {
            return Result.error(403, "当前账号没有该分组的管理权限");
        }
        return Result.success(projectService.deleteGroup(id));
    }

    @GetMapping("/tree")
    public Result<List<ProjectTreeNode>> tree(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestParam(required = false) Long userId) {
        if (userId != null && permissionService.isAdmin(authorization)) {
            return Result.success(projectService.tree(userId));
        }
        return Result.success(projectService.treeByVisibleProjects(permissionService.visibleProjectIds(authorization)));
    }
}
