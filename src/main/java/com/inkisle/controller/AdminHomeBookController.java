package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.dto.HomeBookDTO;
import com.inkisle.service.HomeBookService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端首页推荐位配置
 * 最新更新板块由更新时间实时算出，不在这里配置
 */
@RestController
@RequestMapping("/admin/home")
public class AdminHomeBookController {

    @Autowired
    private HomeBookService homeBookService;

    /**
     * 某个板块的条目，含已下架的书，方便管理员清理失效条目
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam Integer section) {
        return Result.ok(homeBookService.listBySection(section));
    }

    /**
     * 加入推荐位
     */
    @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody HomeBookDTO homeBookDTO) {
        homeBookService.add(homeBookDTO);
        return Result.ok();
    }

    /**
     * 调整排序，值越大越靠前
     */
    @PutMapping("/{id}/sort")
    public Result<Void> updateSort(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        homeBookService.updateSort(id, body.get("sort"));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        homeBookService.delete(id);
        return Result.ok();
    }

}
