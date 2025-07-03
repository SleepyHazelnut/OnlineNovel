package com.inkisle.service;

import com.inkisle.entity.BookComment;

import java.util.List;
import java.util.Map;

public interface BookCommentService {

    List<BookComment> listByBookId(Long bookId, Long viewerId);

    /** 新评论进待审，审核通过后才对其他人展示 */
    void add(BookComment comment);

    Map<String, Object> pageByUser(Long userId, Integer pageNum, Integer pageSize);

    /** 改完回到待审，防止先发合规内容再改成违规的 */
    void updateContent(Long id, Long userId, String content);

    /** 删一级评论时连同其回复一起删 */
    void delete(Long id, Long userId);

    Map<String, Object> pageAll(String keyword, Integer auditStatus, Integer pageNum, Integer pageSize);

    void adminDelete(Long id);

    void pass(Long id, Long adminId);

    void reject(Long id, String remark, Long adminId);

}
