package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.Result;
import com.inkisle.common.RoleConstant;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.mapper.AuthorInfoMapper;
import com.inkisle.service.AuthorService;
import com.inkisle.util.JwtUtil;
import com.inkisle.util.Md5Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthorServiceImpl implements AuthorService {

    @Autowired
    private AuthorInfoMapper authorInfoMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Result<Void> register(AuthorInfo authorInfo) {
        if (authorInfoMapper.countByUsername(authorInfo.getUsername()) > 0) {
            throw new BusinessException("该账号已被注册");
        }

        AuthorInfo author = new AuthorInfo();
        author.setUsername(authorInfo.getUsername());
        author.setPassword(Md5Util.md5(authorInfo.getPassword()));
        // 没填笔名就先拿账号顶上，后面可以在个人中心改
        author.setPenName(authorInfo.getPenName() == null ? authorInfo.getUsername() : authorInfo.getPenName());
        author.setPhone(authorInfo.getPhone());
        author.setEmail(authorInfo.getEmail());
        author.setIntro(authorInfo.getIntro());
        author.setStatus(0);
        // 新注册的作者资质待审，审过了才能发作品
        author.setAuditStatus(0);
        author.setCreateTime(LocalDateTime.now());
        authorInfoMapper.insert(author);

        return Result.ok();
    }

    @Override
    public Result<Map<String, Object>> login(String username, String password) {
        AuthorInfo author = authorInfoMapper.selectByUsername(username);
        if (author == null || !Md5Util.md5(password).equals(author.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        if (author.getStatus() != null && author.getStatus() == 1) {
            throw new BusinessException("账号已被禁用");
        }

        String token = jwtUtil.generate(author.getId(), author.getUsername(), RoleConstant.AUTHOR);

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
