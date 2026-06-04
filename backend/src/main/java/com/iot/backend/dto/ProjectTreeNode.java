package com.iot.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class ProjectTreeNode {
    private Long id;
    private Long projectId;
    private Long parentId;
    private String label;
    private String nodeType;
    private String groupType;
    private Integer deviceCount;
    private List<ProjectTreeNode> children = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getGroupType() { return groupType; }
    public void setGroupType(String groupType) { this.groupType = groupType; }
    public Integer getDeviceCount() { return deviceCount; }
    public void setDeviceCount(Integer deviceCount) { this.deviceCount = deviceCount; }
    public List<ProjectTreeNode> getChildren() { return children; }
    public void setChildren(List<ProjectTreeNode> children) { this.children = children; }
}
