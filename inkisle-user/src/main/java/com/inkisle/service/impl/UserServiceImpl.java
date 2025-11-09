package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.PageUtils;
import com.inkisle.common.Result;
import com.inkisle.common.RoleConstant;
import com.inkisle.common.TokenVersionStore;
import com.inkisle.common.UserStatusCache;
import com.inkisle.dto.PasswordDTO;
import com.inkisle.dto.UserProfileDTO;
import com.inkisle.dto.UserRegisterDTO;
import com.inkisle.entity.UserInfo;
import com.inkisle.mapper.UserInfoMapper;
import com.inkisle.service.UserService;
import com.inkisle.util.JwtUtil;
import com.inkisle.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 读者账号
 * 注册时密码存 BCrypt，用户名靠唯一索引保证不重，登录成功签发一个 USER 角色的 JWT
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    /** 改密时原密码允许错几次、锁多久 */
    private static final int MAX_PASSWORD_FAILS = 5;

    private static final Duration PASSWORD_FAIL_TTL = Duration.ofMinutes(15);

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TokenVersionStore tokenVersionStore;

    @Autowired
    private UserStatusCache userStatusCache;

    @Override
    public UserInfo getById(Long id) {
        return userInfoMapper.selectById(id);
    }

    /**
     * 用户名唯一由 uk_username 保证
     *
     * 原来先 count 再 insert，两次请求之间没有任何保护：两个同名注册同时进来，
     * 都能查到 0 条，然后第二条 insert 抛 DuplicateKeyException，
     * 落到全局兜底变成「操作失败」，用户根本不知道是重名
     */
    @Override
    public Result<Void> register(UserRegisterDTO registerDTO) {
        UserInfo user = new UserInfo();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(PasswordUtil.encode(registerDTO.getPassword()));
        user.setNickName(registerDTO.getUsername());
        user.setStatus(0);
        user.setCreateTime(LocalDateTime.now());
        try {
            userInfoMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("用户名已存在");
        }

        return Result.ok();
    }

    @Override
    public Result<Map<String, Object>> login(String username, String password) {
        UserInfo user = userInfoMapper.selectByUsername(username);
        // 用户不存在与密码错误返回同一提示，避免逐个尝试探测账号是否存在
        if (user == null || !PasswordUtil.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException("账号已被封禁");
        }

        upgradeLegacyPassword(user, password);

        // 带上当前的身份版本号：以后改密、登出、封禁都靠它让这个 token 提前作废
        String token = jwtUtil.generate(user.getId(), user.getUsername(), RoleConstant.USER,
                tokenVersionStore.current(RoleConstant.USER, user.getId()));

        // 返回前清空密码字段，避免随响应体返回
        user.setPassword(null);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return Result.ok(data);
    }

    /**
     * 老记录是无盐 MD5，校验通过了就顺手换成 BCrypt
     * 这里已经确认是本人，改密码不会影响他；失败了也只影响这条记录下次再升级，
     * 不该把登录一起弄挂
     *
     * 更新带上旧哈希做条件：本人可能正在另一台设备上改密码，
     * 无条件覆盖会把刚设好的新密码改回旧的，而用户以为改成功了
     */
    private void upgradeLegacyPassword(UserInfo user, String rawPassword) {
        if (!PasswordUtil.isLegacy(user.getPassword())) {
            return;
        }
        try {
            int rows = userInfoMapper.updatePasswordIfMatch(user.getId(),
                    PasswordUtil.encode(rawPassword), user.getPassword(), LocalDateTime.now());
            if (rows == 0) {
                log.debug("密码已被其他端改过，放弃本次格式升级 userId={}", user.getId());
            }
        } catch (Exception e) {
            log.warn("密码格式升级失败，下次登录再试 userId={}", user.getId(), e);
        }
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
        // 状态缓存删掉，书籍服务和本服务的拦截器下一个请求就能看到封禁
        userStatusCache.evict(RoleConstant.USER, userId);
        if (status == 1) {
            // 封禁要立刻生效，不能等他手上那个 token 自然过期：直接作废全部已签发的会话
            tokenVersionStore.bump(RoleConstant.USER, userId);
        }
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

    /**
     * 改密码
     *
     * 这个接口只认 token，不认人脸：token 一旦泄露（共享设备、XSS、localStorage 被读走），
     * 拿着它无限次猜原密码就能把账号彻底接管，而且改完密码受害者还不掉线，很难察觉。
     * 所以加两件事：错五次锁十五分钟，改完让所有旧会话失效
     */
    @Override
    public void changePassword(Long userId, PasswordDTO passwordDTO) {
        String failKey = passwordFailKey(userId);
        int fails = currentPasswordFails(failKey);
        if (fails >= MAX_PASSWORD_FAILS) {
            throw new BusinessException("原密码错误次数过多，请 "
                    + PASSWORD_FAIL_TTL.toMinutes() + " 分钟后再试");
        }

        String stored = userInfoMapper.selectPasswordById(userId);
        if (stored == null) {
            throw new BusinessException("用户不存在");
        }
        if (!PasswordUtil.matches(passwordDTO.getOldPassword(), stored)) {
            recordPasswordFail(failKey, fails);
            throw new BusinessException("原密码不对");
        }
        if (passwordDTO.getOldPassword().equals(passwordDTO.getNewPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        userInfoMapper.updatePassword(userId, PasswordUtil.encode(passwordDTO.getNewPassword()),
                LocalDateTime.now());
        clearPasswordFail(failKey);
        // 改完密码，之前签出去的 token 全部作废，攻击者拿到的那份也就没用了
        tokenVersionStore.bump(RoleConstant.USER, userId);
        log.info("用户修改密码，已作废旧会话 userId={}", userId);
    }

    @Override
    public void logout(Long userId) {
        // 没有 jti 黑名单，登出就是把版本号加一，所有端一起下线
        tokenVersionStore.bump(RoleConstant.USER, userId);
    }

    private String passwordFailKey(Long userId) {
        return "pwd:fail:" + RoleConstant.USER + ":" + userId;
    }

    private int currentPasswordFails(String key) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Exception e) {
            // 计数读不到就当没失败过：宁可少拦一次，不能把正常用户锁死
            return 0;
        }
    }

    private void recordPasswordFail(String key, int current) {
        try {
            stringRedisTemplate.opsForValue().increment(key);
            // 第一次失败时挂上 TTL，之后每次错误不续期，锁定期从第一次失败开始算
            if (current == 0) {
                stringRedisTemplate.expire(key, PASSWORD_FAIL_TTL);
            }
        } catch (Exception e) {
            log.warn("原密码错误计数写入失败 key={}", key, e);
        }
    }

    private void clearPasswordFail(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("原密码错误计数清理失败 key={}", key, e);
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

}
