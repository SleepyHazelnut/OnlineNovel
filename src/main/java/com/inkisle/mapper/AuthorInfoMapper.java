package com.inkisle.mapper;

import com.inkisle.entity.AuthorInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthorInfoMapper {

    /**
     * 按账号查询，登录时要拿密码
     */
    AuthorInfo selectByUsername(String username);

    /**
     * 账号查重
     */
    int countByUsername(String username);

    AuthorInfo selectById(@Param("id") Long id);

    int insert(AuthorInfo authorInfo);

}
