package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.service.AuthorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端的作者资质审核。作者注册完是待审，这里过了才能发作品
 * 没跟书籍审核放一个 Controller，动的是不同的表和字段
 */
@RestController
@RequestMapping("/admin/author")
public class AdminAuthorController {

    @Autowired
    private AuthorService authorService;

    /**
     * 作者列表，默认看资质待审的（auditStatus=0）
     */
    @GetMapping("/list")
    public Result<List<AuthorInfo>> list(@RequestParam(defaultValue = "0") Integer auditStatus) {
        return Result.ok(authorService.listByAuditStatus(auditStatus));
    }

    /**
     * 资质审核：status 传 1 是通过、2 是驳回，驳回必须带 remark
     */
    @PostMapping("/audit")
    public Result<Void> audit(@RequestBody Map<String, Object> params) {
        Long authorId = Long.valueOf(params.get("authorId").toString());
        Integer status = Integer.valueOf(params.get("status").toString());
        authorService.audit(authorId, status, (String) params.get("remark"));
        return Result.ok();
    }

}
