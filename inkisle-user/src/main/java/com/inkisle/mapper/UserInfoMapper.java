package com.inkisle.mapper;

import com.inkisle.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserInfoMapper {

    /**
     * 按用户名查，登录用。username 有唯一索引，最多一条，所以返回单个对象
     */
    UserInfo selectByUsername(String username);

    /**
     * 注册前查一下用户名有没有被占
     */
    int countByUsername(String username);

    UserInfo selectById(@Param("id") Long id);

    int insert(UserInfo userInfo);

    /**
     * 管理端用户列表，offset 和 pageSize 也是上层算好传进来的
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

    /**
     * 只在库里还是这条旧哈希时才替换。存量密码升级用：
     * 无条件 UPDATE 会和本人改密并发，把刚设好的新密码覆盖回旧的
     */
    int updatePasswordIfMatch(@Param("id") Long id,
                              @Param("password") String password,
                              @Param("oldPassword") String oldPassword,
                              @Param("updateTime") java.time.LocalDateTime updateTime);

}
