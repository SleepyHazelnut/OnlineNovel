package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.PageUtils;
import com.inkisle.common.Result;
import com.inkisle.common.RoleConstant;
import com.inkisle.dto.PasswordDTO;
import com.inkisle.dto.UserProfileDTO;
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
 * 注册时密码存 MD5，用户名要查重，登录成功签发一个 USER 角色的 JWT
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
        int num = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, 10);
        Map<String, Object> data = new HashMap<>();
        data.put("list", userInfoMapper.selectPage(PageUtils.offset(num, size), size));
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

    @Override
    public UserInfo updateProfile(Long userId, UserProfileDTO profileDTO) {
        UserInfo exist = userInfoMapper.selectById(userId);
        if (exist == null) {
            throw new BusinessException("用户不存在");
        }

        String nickName = profileDTO.getNickName() == null ? "" : profileDTO.getNickName().trim();
        if (nickName.isEmpty()) {
            throw new BusinessException("昵称不能为空");
        }

        // 空串一律存 null：页面上「未填」与「填了个空串」应该是一回事，
        // 否则每次保存都会在库里留下一条 '' 的记录
        userInfoMapper.updateProfile(userId, nickName,
                blankToNull(profileDTO.getPhone()),
                blankToNull(profileDTO.getEmail()),
                blankToNull(profileDTO.getAvatar()),
                LocalDateTime.now());
        // 重新查一次，返回给前端的是库里的最新值；响应里仍不含密码
        return userInfoMapper.selectById(userId);
    }

    @Override
    public void changePassword(Long userId, PasswordDTO passwordDTO) {
        String stored = userInfoMapper.selectPasswordById(userId);
        if (stored == null) {
            throw new BusinessException("用户不存在");
        }
        if (!Md5Util.md5(passwordDTO.getOldPassword()).equals(stored)) {
            throw new BusinessException("原密码不对");
        }
        if (passwordDTO.getOldPassword().equals(passwordDTO.getNewPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        userInfoMapper.updatePassword(userId, Md5Util.md5(passwordDTO.getNewPassword()), LocalDateTime.now());
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

}
