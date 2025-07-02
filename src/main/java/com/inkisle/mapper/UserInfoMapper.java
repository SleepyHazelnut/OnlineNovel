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

    /**
     * 只更新 status，封禁 / 解封用
     */
    int updateStatus(UserInfo userInfo);

    /**
     * 更新个人资料。密码不在这个语句里改，避免资料保存顺手把密码覆盖
     */
    int updateProfile(@Param("id") Long id,
                      @Param("nickName") String nickName,
                      @Param("phone") String phone,
                      @Param("email") String email,
                      @Param("avatar") String avatar,
                      @Param("updateTime") LocalDateTime updateTime);

    /**
     * 单独取密码摘要，供修改密码时校验原密码
     * 常规查询的列清单刻意不含 password，不能为了这一处又把密码带进所有响应
     */
    String selectPasswordById(@Param("id") Long id);

    int updatePassword(@Param("id") Long id,
                       @Param("password") String password,
                       @Param("updateTime") LocalDateTime updateTime);

}
