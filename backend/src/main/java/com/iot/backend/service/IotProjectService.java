package com.iot.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.iot.backend.dto.ProjectTreeNode;
import com.iot.backend.entity.IotCommDevice;
import com.iot.backend.entity.IotProject;
import com.iot.backend.entity.IotProjectGroup;
import com.iot.backend.entity.SysUser;
import com.iot.backend.entity.SysUserProject;
import com.iot.backend.mapper.IotCommDeviceMapper;
import com.iot.backend.mapper.IotProjectGroupMapper;
import com.iot.backend.mapper.IotProjectMapper;
import com.iot.backend.mapper.SysUserMapper;
import com.iot.backend.mapper.SysUserProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IotProjectService {

    @Autowired
    private IotProjectMapper projectMapper;

    @Autowired
    private IotProjectGroupMapper groupMapper;

    @Autowired
    private IotCommDeviceMapper deviceMapper;

    @Autowired
    private SysUserProjectMapper userProjectMapper;

    @Autowired
    private SysUserMapper userMapper;

    public List<IotProject> listProjects() {
        return projectMapper.selectList(new LambdaQueryWrapper<IotProject>().orderByAsc(IotProject::getId));
    }

    public List<IotProject> listProjects(List<Long> visibleProjectIds) {
        if (visibleProjectIds == null) {
            return listProjects();
        }
        if (visibleProjectIds.isEmpty()) {
            return new ArrayList<>();
        }
        return projectMapper.selectList(new LambdaQueryWrapper<IotProject>()
                .in(IotProject::getId, visibleProjectIds)
                .orderByAsc(IotProject::getId));
    }

    public IotProject createProject(IotProject project) {
        if (project.getStatus() == null) {
            project.setStatus("ACTIVE");
        }
        projectMapper.insert(project);
        return project;
    }

    public IotProject updateProject(Long id, IotProject project) {
        project.setId(id);
        projectMapper.updateById(project);
        return projectMapper.selectById(id);
    }

    public boolean deleteProject(Long id) {
        deviceMapper.update(null, new LambdaUpdateWrapper<IotCommDevice>()
                .eq(IotCommDevice::getProjectId, id)
                .set(IotCommDevice::getProjectId, null)
                .set(IotCommDevice::getGroupId, null));
        groupMapper.delete(new LambdaQueryWrapper<IotProjectGroup>().eq(IotProjectGroup::getProjectId, id));
        return projectMapper.deleteById(id) > 0;
    }

    public List<IotProjectGroup> listGroups(Long projectId) {
        LambdaQueryWrapper<IotProjectGroup> wrapper = new LambdaQueryWrapper<IotProjectGroup>()
                .orderByAsc(IotProjectGroup::getSortNo)
                .orderByAsc(IotProjectGroup::getId);
        if (projectId != null) {
            wrapper.eq(IotProjectGroup::getProjectId, projectId);
        }
        return groupMapper.selectList(wrapper);
    }

    public List<IotProjectGroup> listGroups(Long projectId, List<Long> visibleProjectIds) {
        if (visibleProjectIds == null) {
            return listGroups(projectId);
        }
        if (visibleProjectIds.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<IotProjectGroup> wrapper = new LambdaQueryWrapper<IotProjectGroup>()
                .orderByAsc(IotProjectGroup::getSortNo)
                .orderByAsc(IotProjectGroup::getId);
        if (projectId != null) {
            if (!visibleProjectIds.contains(projectId)) {
                return new ArrayList<>();
            }
            wrapper.eq(IotProjectGroup::getProjectId, projectId);
        } else {
            wrapper.in(IotProjectGroup::getProjectId, visibleProjectIds);
        }
        return groupMapper.selectList(wrapper);
    }

    public IotProjectGroup createGroup(IotProjectGroup group) {
        if (group.getParentId() == null) {
            group.setParentId(0L);
        }
        if (group.getGroupType() == null) {
            group.setGroupType("AREA");
        }
        if (group.getSortNo() == null) {
            group.setSortNo(0);
        }
        groupMapper.insert(group);
        return group;
    }

    public IotProjectGroup updateGroup(Long id, IotProjectGroup group) {
        group.setId(id);
        groupMapper.updateById(group);
        return groupMapper.selectById(id);
    }

    public boolean deleteGroup(Long id) {
        deviceMapper.update(null, new LambdaUpdateWrapper<IotCommDevice>()
                .eq(IotCommDevice::getGroupId, id)
                .set(IotCommDevice::getGroupId, null));
        groupMapper.update(null, new LambdaUpdateWrapper<IotProjectGroup>()
                .eq(IotProjectGroup::getParentId, id)
                .set(IotProjectGroup::getParentId, 0L));
        return groupMapper.deleteById(id) > 0;
    }

    public List<ProjectTreeNode> tree(Long userId) {
        List<IotProject> projects = listProjects();
        if (userId != null) {
            SysUser user = userMapper.selectById(userId);
            if (user == null) {
                projects.clear();
            } else if (!"admin".equalsIgnoreCase(user.getRoleKey())) {
                List<Long> allowedProjectIds = new ArrayList<>();
                List<SysUserProject> permissions = userProjectMapper.selectList(
                        new LambdaQueryWrapper<SysUserProject>().eq(SysUserProject::getUserId, userId)
                );
                for (SysUserProject permission : permissions) {
                    allowedProjectIds.add(permission.getProjectId());
                }
                projects.removeIf(project -> !allowedProjectIds.contains(project.getId()));
            }
        }
        List<IotProjectGroup> groups = listGroups(null);
        Map<Long, Integer> deviceCounts = loadDeviceCounts();

        Map<Long, List<IotProjectGroup>> groupsByProject = new HashMap<>();
        for (IotProjectGroup group : groups) {
            groupsByProject.computeIfAbsent(group.getProjectId(), key -> new ArrayList<>()).add(group);
        }

        List<ProjectTreeNode> roots = new ArrayList<>();
        for (IotProject project : projects) {
            ProjectTreeNode root = new ProjectTreeNode();
            root.setId(project.getId());
            root.setProjectId(project.getId());
            root.setParentId(0L);
            root.setLabel(project.getProjectName());
            root.setNodeType("PROJECT");
            root.setGroupType("PROJECT");
            root.setDeviceCount(countProjectDevices(project.getId(), deviceCounts, groupsByProject.get(project.getId())));
            root.setChildren(buildGroupTree(project.getId(), 0L, groupsByProject.get(project.getId()), deviceCounts));
            roots.add(root);
        }
        return roots;
    }

    public List<ProjectTreeNode> treeByVisibleProjects(List<Long> visibleProjectIds) {
        List<IotProject> projects = listProjects(visibleProjectIds);
        List<IotProjectGroup> groups = listGroups(null, visibleProjectIds);
        Map<Long, Integer> deviceCounts = loadDeviceCounts(visibleProjectIds);

        Map<Long, List<IotProjectGroup>> groupsByProject = new HashMap<>();
        for (IotProjectGroup group : groups) {
            groupsByProject.computeIfAbsent(group.getProjectId(), key -> new ArrayList<>()).add(group);
        }

        List<ProjectTreeNode> roots = new ArrayList<>();
        for (IotProject project : projects) {
            ProjectTreeNode root = new ProjectTreeNode();
            root.setId(project.getId());
            root.setProjectId(project.getId());
            root.setParentId(0L);
            root.setLabel(project.getProjectName());
            root.setNodeType("PROJECT");
            root.setGroupType("PROJECT");
            root.setDeviceCount(countProjectDevices(project.getId(), deviceCounts, groupsByProject.get(project.getId())));
            root.setChildren(buildGroupTree(project.getId(), 0L, groupsByProject.get(project.getId()), deviceCounts));
            roots.add(root);
        }
        return roots;
    }

    private List<ProjectTreeNode> buildGroupTree(Long projectId, Long parentId, List<IotProjectGroup> groups, Map<Long, Integer> deviceCounts) {
        List<ProjectTreeNode> nodes = new ArrayList<>();
        if (groups == null) {
            return nodes;
        }

        for (IotProjectGroup group : groups) {
            if (!parentId.equals(group.getParentId())) {
                continue;
            }

            ProjectTreeNode node = new ProjectTreeNode();
            node.setId(group.getId());
            node.setProjectId(projectId);
            node.setParentId(group.getParentId());
            node.setLabel(group.getGroupName());
            node.setNodeType("GROUP");
            node.setGroupType(group.getGroupType());
            node.setDeviceCount(deviceCounts.getOrDefault(group.getId(), 0));
            node.setChildren(buildGroupTree(projectId, group.getId(), groups, deviceCounts));
            nodes.add(node);
        }
        return nodes;
    }

    private Map<Long, Integer> loadDeviceCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        deviceMapper.selectList(null).forEach(device -> {
            if (device.getGroupId() != null) {
                counts.put(device.getGroupId(), counts.getOrDefault(device.getGroupId(), 0) + 1);
            }
        });
        return counts;
    }

    private Map<Long, Integer> loadDeviceCounts(List<Long> visibleProjectIds) {
        if (visibleProjectIds == null) {
            return loadDeviceCounts();
        }
        Map<Long, Integer> counts = new HashMap<>();
        if (visibleProjectIds.isEmpty()) {
            return counts;
        }
        deviceMapper.selectList(new LambdaQueryWrapper<IotCommDevice>()
                .in(IotCommDevice::getProjectId, visibleProjectIds))
                .forEach(device -> {
                    if (device.getGroupId() != null) {
                        counts.put(device.getGroupId(), counts.getOrDefault(device.getGroupId(), 0) + 1);
                    }
                });
        return counts;
    }

    private int countProjectDevices(Long projectId, Map<Long, Integer> deviceCounts, List<IotProjectGroup> groups) {
        if (groups == null) {
            return 0;
        }
        int total = 0;
        for (IotProjectGroup group : groups) {
            total += deviceCounts.getOrDefault(group.getId(), 0);
        }
        return total;
    }
}
