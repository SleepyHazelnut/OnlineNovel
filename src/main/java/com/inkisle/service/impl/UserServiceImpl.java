package com.inkisle.service.impl;

import com.inkisle.common.Result;
import com.inkisle.entity.UserInfo;
import com.inkisle.mapper.UserInfoMapper;
import com.inkisle.service.UserService;
import com.inkisle.util.Md5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    public List<UserInfo> listAll() {
        return userInfoMapper.selectAll();
    }

    @Override
    public Result register(UserInfo userInfo) {
        // 用户名唯一，先查重
        if (userInfoMapper.countByUsername(userInfo.getUsername()) > 0) {
            return Result.fail("用户名已存在");
        }

        UserInfo user = new UserInfo();
        user.setUsername(userInfo.getUsername());
        user.setPassword(Md5Util.md5(userInfo.getPassword()));
        user.setNickName(userInfo.getUsername());
        user.setCreateTime(LocalDateTime.now());
        userInfoMapper.insert(user);

        return Result.ok();
    }

    @Override
    public Result login(String username, String password) {
        UserInfo user = userInfoMapper.selectByUsername(username);
        // 用户不存在和密码错误返回同样的提示，避免被人试出哪些账号存在
        if (user == null || !Md5Util.md5(password).equals(user.getPassword())) {
            return Result.fail("用户名或密码错误");
        }

        // 密码不要返回给前端
        user.setPassword(null);
        return Result.ok(user);
    }

}
