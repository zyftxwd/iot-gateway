package com.iot.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.iot.backend.entity.SysUser;

/**
 * 用户业务逻辑接口
 */
public interface ISysUserService extends IService<SysUser> {

    // 这里可以定义你自己特有的业务方法
    // 比如：根据用户名查询完整信息 (包含部门名称等)
    SysUser getUserInfoByUsername(String username);
}