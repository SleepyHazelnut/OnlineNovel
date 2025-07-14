package com.inkisle.mapper;

import com.inkisle.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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

    int updateStatus(UserInfo userInfo);

    /** 只改资料，密码不在这条语句里 */
    int updateProfile(@Param("id") Long id,
                      @Param("nickName") String nickName,
                      @Param("phone") String phone,
                      @Param("email") String email,
                      @Param("avatar") String avatar,
                      @Param("updateTime") LocalDateTime updateTime);

    /** 密码单独取，常规查询的列清单里不带 password */
    String selectPasswordById(@Param("id") Long id);

    int updatePassword(@Param("id") Long id,
                       @Param("password") String password,
                       @Param("updateTime") LocalDateTime updateTime);

}
