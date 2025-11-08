package com.inkisle.mapper;

import com.inkisle.entity.BookAuditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookAuditRecordMapper {

    /**
     * 按审核状态查询列表，并带出书名
     * 审核表仅存 book_id，书名由 XML 中 LEFT JOIN book_info 取得
     */
    List<BookAuditRecord> selectByStatus(@Param("status") Integer status);

    BookAuditRecord selectById(@Param("id") Long id);

    int insert(BookAuditRecord record);

    /**
     * 仅更新审核结果相关字段，提交人与提交时间保持不变。
     * 带 status = 0 前置条件，返回 0 表示这条已被别人审过
     */
    int updateAuditIfPending(BookAuditRecord record);

    /** 后台列表分页 */
    List<BookAuditRecord> selectPageByStatus(@Param("status") Integer status,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    int countByStatus(@Param("status") Integer status);

    /**
     * 统计该书是否存在待审核记录，用于阻止作者重复提交
     */
    int countPending(@Param("bookId") Long bookId);

    /**
     * 最新一条审核记录，供作者查看作品当前状态
     * 按 id 倒序取一条，不用 submit_time，因为同一秒内提交无法区分先后
     */
    BookAuditRecord selectLatestByBookId(@Param("bookId") Long bookId);

    /**
     * 删除某本书的全部审核记录。作品被删后残留的待审记录会在管理端变成死链
     */
    int deleteByBookId(@Param("bookId") Long bookId);

}
