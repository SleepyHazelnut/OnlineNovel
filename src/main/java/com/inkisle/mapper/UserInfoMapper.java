package com.inkisle.mapper;

import com.inkisle.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserInfoMapper {

    /**
     * 按用户名查询，供登录使用。username 有唯一索引，最多一条，故返回单个对象
     */
    UserInfo selectByUsername(String username);

    /**
     * 按用户名统计，用于注册查重
     */
    int countByUsername(String username);

    UserInfo selectById(@Param("id") Long id);

    int insert(UserInfo userInfo);

    /**
     * 用户分页，管理端用
     */
    List<UserInfo> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    int countAll();

    /**
     * 只更新状态，封禁 / 解封
     */
    int updateStatus(UserInfo userInfo);

}
