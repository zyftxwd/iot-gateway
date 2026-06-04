package com.iot.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("iot_project_group")
public class IotProjectGroup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long parentId;
    private String groupName;
    private String groupType;
    private Integer sortNo;
    private String remark;
}
