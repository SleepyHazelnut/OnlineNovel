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
     * 注册查重，统计该用户名是否已被占用
     */
    int countByUsername(String username);

    UserInfo selectById(@Param("id") Long id);

    int insert(UserInfo userInfo);

    /**
     * 管理端用户列表，offset 与 pageSize 同样由上层计算后传入
     */
    List<UserInfo> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    int countAll();

    /**
     * 只更新 status，封禁 / 解封用
     */
    int updateStatus(UserInfo userInfo);

}
