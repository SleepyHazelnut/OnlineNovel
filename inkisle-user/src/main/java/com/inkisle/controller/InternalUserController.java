package com.inkisle.controller;

import com.inkisle.common.RoleConstant;
import com.inkisle.entity.AdminInfo;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.entity.UserInfo;
import com.inkisle.mapper.AdminInfoMapper;
import com.inkisle.mapper.AuthorInfoMapper;
import com.inkisle.mapper.UserInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 给别的服务查账号状态用
 * <p>
 * 三张账号表各自自增，同一个 id 在不同角色下是三个人，
 * 故必须连角色一起传，只给 id 会查错表
 */
@RestController
@RequestMapping("/internal/user")
public class InternalUserController {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private AuthorInfoMapper authorInfoMapper;

    @Autowired
    private AdminInfoMapper adminInfoMapper;

    /**
     * 0 正常，1 封禁，账号不存在返回 null
     * 不缓存：缓存会让封禁延迟生效
     */
    @GetMapping("/status")
    public Integer status(@RequestParam String role, @RequestParam Long id) {
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
