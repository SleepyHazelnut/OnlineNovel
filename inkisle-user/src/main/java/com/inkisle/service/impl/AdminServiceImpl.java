package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.Result;
import com.inkisle.common.RoleConstant;
import com.inkisle.entity.AdminInfo;
import com.inkisle.mapper.AdminInfoMapper;
import com.inkisle.service.AdminService;
import com.inkisle.util.JwtUtil;
import com.inkisle.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理端登录。管理员单独一张表，流程跟读者一样，
 * 密码存 BCrypt，token 也是 JWT，只是角色签发成 ADMIN
 */
@Slf4j
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
        if (admin == null || !PasswordUtil.matches(password, admin.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }

        upgradeLegacyPassword(admin, password);

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

    /** 老记录在登录校验通过后原地换成 BCrypt；失败不影响本次登录 */
    private void upgradeLegacyPassword(AdminInfo admin, String rawPassword) {
        if (!PasswordUtil.isLegacy(admin.getPassword())) {
            return;
        }
        try {
            adminInfoMapper.updatePassword(admin.getId(), PasswordUtil.encode(rawPassword), LocalDateTime.now());
        } catch (Exception e) {
            log.warn("密码格式升级失败，下次登录再试 adminId={}", admin.getId(), e);
        }
    }

}
