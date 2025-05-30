package com.inkisle.mapper;

import com.inkisle.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserInfoMapper {

    /**
     * 查询全部用户
     */
    List<UserInfo> selectAll();

    /**
     * 按用户名查询，用户名有唯一索引所以最多一条
     */
    UserInfo selectByUsername(String username);

    /**
     * 按用户名统计，用于注册查重
     */
    int countByUsername(String username);

    int insert(UserInfo userInfo);

}
