package com.inkisle.service.support;

import com.inkisle.common.RoleConstant;
import com.inkisle.entity.AdminInfo;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.entity.UserInfo;
import com.inkisle.mapper.AdminInfoMapper;
import com.inkisle.mapper.AuthorInfoMapper;
import com.inkisle.mapper.UserInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 按角色查账号状态，三张账号表各管各的自增
 *
 * 同一个 id 在读者、作者、管理员里是三个人，所以必须带角色一起查，光给 id 会查错表。
 * 内部接口和本服务的拦截器都用它，避免两处各写一份、口径慢慢走偏
 */
@Component
public class AccountStatusResolver {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private AuthorInfoMapper authorInfoMapper;

    @Autowired
    private AdminInfoMapper adminInfoMapper;

    /** @return 0 正常、1 封禁、null 账号不存在 */
    public Integer resolve(String role, Long id) {
        if (RoleConstant.USER.equals(role)) {
            UserInfo user = userInfoMapper.selectById(id);
            return user == null ? null : user.getStatus();
        }
        if (RoleConstant.AUTHOR.equals(role)) {
            AuthorInfo author = authorInfoMapper.selectById(id);
            return author == null ? null : author.getStatus();
        }
        if (RoleConstant.ADMIN.equals(role)) {
            // 管理员没有状态列，停用就是删记录，在即视为正常
            AdminInfo admin = adminInfoMapper.selectById(id);
            return admin == null ? null : 0;
        }
        return null;
    }

}
