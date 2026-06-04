package com.iot.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.iot.backend.entity.SysUser;
import com.iot.backend.mapper.SysUserMapper;
import com.iot.backend.service.ISysUserService;
import org.springframework.stereotype.Service;

/**
 * 用户业务逻辑实现类
 */
@Service // 告诉 Spring 这是一个服务类，交由 Spring 管理
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    @Override
    public SysUser getUserInfoByUsername(String username) {
        // 使用 MyBatis-Plus 的 QueryWrapper 进行条件查询
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username); // WHERE username = 'li_shifu'

        // baseMapper 是 ServiceImpl 提供的，它就是你的 SysUserMapper
        return baseMapper.selectOne(queryWrapper);
    }
}