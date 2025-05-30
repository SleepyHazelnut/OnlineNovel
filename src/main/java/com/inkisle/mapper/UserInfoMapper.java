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

}
