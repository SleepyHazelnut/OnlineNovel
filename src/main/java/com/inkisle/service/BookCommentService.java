package com.inkisle.service;

import com.inkisle.entity.BookComment;

import java.util.List;
import java.util.Map;

public interface BookCommentService {

    /**
     * 评论列表，一级评论下挂载其回复
     * 只返回审核通过的；viewerId 非空时额外带上该读者自己的评论
     */
    List<BookComment> listByBookId(Long bookId, Long viewerId);

    /**
     * 发表评论或回复。新评论进入待审，审核通过后才对其他读者展示
     */
    void add(BookComment comment);

    /**
     * 某位读者的评论，分页返回，按时间倒序
     * 回复同样计入，读者在「我的评论」里能看到自己发过的全部内容
     */
    Map<String, Object> pageByUser(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 修改评论内容，仅本人可改。修改后回到待审
     * 参数中的 userId 取自登录态，不接收前端传入
     */
    void updateContent(Long id, Long userId, String content);

    /**
     * 删除评论，仅本人可删。一级评论的回复一并删除
     */
    void delete(Long id, Long userId);

    /**
     * 管理端评论列表，可按内容、书名、昵称与审核状态过滤
     */
    Map<String, Object> pageAll(String keyword, Integer auditStatus, Integer pageNum, Integer pageSize);

    /**
     * 管理端删除评论，不做归属校验
     * 一级评论的回复一并删除，避免留下挂不上父节点的孤立回复
     */
    void adminDelete(Long id);

    /**
     * 审核通过，评论随即对读者可见
     */
    void pass(Long id, Long adminId);

    /**
     * 审核驳回，必须写原因
     */
    void reject(Long id, String remark, Long adminId);

}
