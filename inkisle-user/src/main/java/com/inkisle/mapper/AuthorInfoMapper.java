package com.inkisle.mapper;

import com.inkisle.entity.AuthorInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthorInfoMapper {

    /**
     * 按账号查询，供登录使用。使用包含 password 的列清单，不可由其他查询替代
     */
    AuthorInfo selectByUsername(String username);

    /**
     * 查重，注册前看看 username 有没有人用
     */
    int countByUsername(String username);

    AuthorInfo selectById(@Param("id") Long id);

    int insert(AuthorInfo authorInfo);

    /**
     * 按资质审核状态查作者。映射用的是不带 password 的那份列清单
     */
    List<AuthorInfo> selectByAuditStatus(@Param("auditStatus") Integer auditStatus);

    /**
     * 只改资质审核结果，提交时间那些不动
     */
    int updateAudit(AuthorInfo authorInfo);

    /** 老格式密码在登录校验通过后原地换成 BCrypt，用户无需改密 */
    int updatePassword(@Param("id") Long id,
                       @Param("password") String password,
                       @Param("updateTime") java.time.LocalDateTime updateTime);

}
