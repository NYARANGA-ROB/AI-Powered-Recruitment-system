package com.smartats.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartats.module.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * User mapper interface
 *
 * @Mapper: the MyBatis-Plus scanning annotation
 * BaseMapper: provides the basic CRUD methods
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
