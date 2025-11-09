package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.Result;
import com.inkisle.common.RoleConstant;
import com.inkisle.common.TokenVersionStore;
import com.inkisle.dto.AuthorRegisterDTO;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.mapper.AuthorInfoMapper;
import com.inkisle.service.AuthorService;
import com.inkisle.util.JwtUtil;
import com.inkisle.util.PasswordUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者的注册、登录和资质审核
 * 作者单独一张表，注册完资质待审，管理端过了才能发作品
 */
@Slf4j
@Service
public class AuthorServiceImpl implements AuthorService {

    @Autowired
    private AuthorInfoMapper authorInfoMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenVersionStore tokenVersionStore;

    @Override
    public Result<Void> register(AuthorRegisterDTO registerDTO) {
        if (authorInfoMapper.countByUsername(registerDTO.getUsername()) > 0) {
            throw new BusinessException("该账号已被注册");
        }

        AuthorInfo author = new AuthorInfo();
        author.setUsername(registerDTO.getUsername());
        author.setPassword(PasswordUtil.encode(registerDTO.getPassword()));
        // 未填写笔名时以账号代替，避免列表显示为空，之后可在个人中心修改
        author.setPenName(registerDTO.getPenName() == null || registerDTO.getPenName().isEmpty()
                ? registerDTO.getUsername() : registerDTO.getPenName());
        author.setEmail(registerDTO.getEmail());
        author.setStatus(0);
        // 新作者资质默认待审，审核通过后方可发布作品
        author.setAuditStatus(0);
        author.setCreateTime(LocalDateTime.now());
        authorInfoMapper.insert(author);

        return Result.ok();
    }

    @Override
    public Result<Map<String, Object>> login(String username, String password) {
        AuthorInfo author = authorInfoMapper.selectByUsername(username);
        if (author == null || !PasswordUtil.matches(password, author.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        if (author.getStatus() != null && author.getStatus() == 1) {
            throw new BusinessException("账号已被禁用");
        }

        upgradeLegacyPassword(author, password);

        String token = jwtUtil.generate(author.getId(), author.getUsername(), RoleConstant.AUTHOR,
                tokenVersionStore.current(RoleConstant.AUTHOR, author.getId()));

        author.setPassword(null);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("author", author);
        return Result.ok(data);
    }

    @Override
    public AuthorInfo getById(Long id) {
        return authorInfoMapper.selectById(id);
    }

    /** 老记录在登录校验通过后原地换成 BCrypt；失败不影响本次登录 */
    private void upgradeLegacyPassword(AuthorInfo author, String rawPassword) {
        if (!PasswordUtil.isLegacy(author.getPassword())) {
            return;
        }
        try {
            // 条件更新：本人可能正在别的设备上改密码，无条件覆盖会把新密码改回去
            authorInfoMapper.updatePasswordIfMatch(author.getId(), PasswordUtil.encode(rawPassword),
                    author.getPassword(), LocalDateTime.now());
        } catch (Exception e) {
            log.warn("密码格式升级失败，下次登录再试 authorId={}", author.getId(), e);
        }
    }

    @Override
    public List<AuthorInfo> listByAuditStatus(Integer auditStatus) {
        return authorInfoMapper.selectByAuditStatus(auditStatus);
    }

    @Override
    public void audit(Long authorId, Integer status, String remark) {
        AuthorInfo author = authorInfoMapper.selectById(authorId);
        if (author == null) {
            throw new BusinessException("作者不存在");
        }
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException("审核结果不对");
        }
        // 驳回必须填写原因，否则作者无法判断需修改的内容
        if (status == 2 && (remark == null || remark.trim().isEmpty())) {
            throw new BusinessException("驳回要写原因");
        }

        AuthorInfo update = new AuthorInfo();
        update.setId(authorId);
        update.setAuditStatus(status);
        update.setAuditRemark(remark);
        update.setUpdateTime(LocalDateTime.now());
        authorInfoMapper.updateAudit(update);
    }

}
