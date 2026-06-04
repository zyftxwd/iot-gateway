package com.iot.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.backend.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * SysUser 数据库访问接口
 * 继承 BaseMapper 即可获得强大的 CRUD 能力
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    // MyBatis-Plus 已经帮你写好了 insert, delete, update, selectById 等方法
}