package com.inkisle.mapper;

import com.inkisle.entity.AdminInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminInfoMapper {

    /**
     * 按账号查询，供登录校验使用。必须查出 password 字段，否则无法比对密码
     */
    AdminInfo selectByUsername(String username);

    // XML 中的详情查询未包含 password 列，不可用于登录校验
    AdminInfo selectById(@Param("id") Long id);

    /** 老格式密码在登录校验通过后原地换成 BCrypt，用户无需改密 */
    int updatePassword(@Param("id") Long id,
                       @Param("password") String password,
                       @Param("updateTime") java.time.LocalDateTime updateTime);

    /** 存量密码升级：只有库里仍是这条旧哈希时才替换，避免覆盖并发改密的结果 */
    int updatePasswordIfMatch(@Param("id") Long id,
                              @Param("password") String password,
                              @Param("oldPassword") String oldPassword,
                              @Param("updateTime") java.time.LocalDateTime updateTime);

}
