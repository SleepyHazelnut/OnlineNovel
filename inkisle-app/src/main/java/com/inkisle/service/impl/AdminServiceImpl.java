package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.Result;
import com.inkisle.common.RoleConstant;
import com.inkisle.entity.AdminInfo;
import com.inkisle.mapper.AdminInfoMapper;
import com.inkisle.service.AdminService;
import com.inkisle.util.JwtUtil;
import com.inkisle.util.Md5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端登录
 * 管理员使用独立数据表，登录流程与读者一致：
 * 密码以 MD5 存储，token 使用 JWT，仅角色签发为 ADMIN
 */
@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private AdminInfoMapper adminInfoMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Result<Map<String, Object>> login(String username, String password) {
        AdminInfo admin = adminInfoMapper.selectByUsername(username);
        // 查不到用户与密码错误返回同一提示，避免暴露账号是否存在
        if (admin == null || !Md5Util.md5(password).equals(admin.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }

        String token = jwtUtil.generate(admin.getId(), admin.getUsername(), RoleConstant.ADMIN);

        // 返回前端前清空密码字段
        admin.setPassword(null);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("admin", admin);
        return Result.ok(data);
    }

    @Override
    public AdminInfo getById(Long id) {
        return adminInfoMapper.selectById(id);
    }

}
