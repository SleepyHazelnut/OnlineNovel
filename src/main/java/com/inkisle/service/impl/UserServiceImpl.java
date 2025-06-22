package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.Result;
import com.inkisle.common.RoleConstant;
import com.inkisle.dto.UserRegisterDTO;
import com.inkisle.entity.UserInfo;
import com.inkisle.mapper.UserInfoMapper;
import com.inkisle.service.UserService;
import com.inkisle.util.JwtUtil;
import com.inkisle.util.Md5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 读者账号
 * 注册时密码以 MD5 存储，用户名需查重；登录成功后签发带 USER 角色的 JWT 返回
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public UserInfo getById(Long id) {
        return userInfoMapper.selectById(id);
    }

    @Override
    public Result<Void> register(UserRegisterDTO registerDTO) {
        // 用户名需唯一，插入前先查询是否已存在
        if (userInfoMapper.countByUsername(registerDTO.getUsername()) > 0) {
            throw new BusinessException("用户名已存在");
        }

        UserInfo user = new UserInfo();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(Md5Util.md5(registerDTO.getPassword()));
        user.setNickName(registerDTO.getUsername());
        user.setStatus(0);
        user.setCreateTime(LocalDateTime.now());
        userInfoMapper.insert(user);

        return Result.ok();
    }

    @Override
    public Result<Map<String, Object>> login(String username, String password) {
        UserInfo user = userInfoMapper.selectByUsername(username);
        // 用户不存在与密码错误返回同一提示，避免逐个尝试探测账号是否存在
        if (user == null || !Md5Util.md5(password).equals(user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException("账号已被封禁");
        }

        String token = jwtUtil.generate(user.getId(), user.getUsername(), RoleConstant.USER);

        // 返回前清空密码字段，避免随响应体返回
        user.setPassword(null);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return Result.ok(data);
    }

    @Override
    public Map<String, Object> page(Integer pageNum, Integer pageSize) {
        int offset = (pageNum - 1) * pageSize;
        Map<String, Object> data = new HashMap<>();
        data.put("list", userInfoMapper.selectPage(offset, pageSize));
        data.put("total", userInfoMapper.countAll());
        return data;
    }

    @Override
    public void updateStatus(Long userId, Integer status) {
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不对");
        }

        UserInfo update = new UserInfo();
        update.setId(userId);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        userInfoMapper.updateStatus(update);
    }

}
