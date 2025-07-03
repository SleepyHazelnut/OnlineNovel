package com.inkisle.service;

import java.util.Map;

/**
 * 章节审核
 * 章节与评论是正文与读者发言的落点，违规内容最常出现在这里，
 * 故在作品整本审核之外单独留一条处置链路
 */
public interface ChapterAuditService {

    /**
     * 按审核状态分页查章节，带书名，供管理端审核列表使用
     */
    Map<String, Object> pageByStatus(Integer auditStatus, Integer pageNum, Integer pageSize);

    /**
     * 审核通过，读者端目录与正文随即可见
     */
    void pass(Long chapterId, Long adminId);

    /**
     * 驳回，必须写原因。驳回后作者可修改章节，保存时会重新进入待审
     */
    void reject(Long chapterId, String remark, Long adminId);

    /**
     * 审核预览：章节信息与正文。未过审的章节走不了读者端接口，审核员需要单独入口
     */
    Map<String, Object> detail(Long chapterId);

}
