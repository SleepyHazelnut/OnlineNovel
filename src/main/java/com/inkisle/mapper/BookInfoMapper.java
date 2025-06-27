package com.inkisle.mapper;

import com.inkisle.entity.BookInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 书籍主表
 * 读者端列表涉及「审核可见性」与「关键词搜索」两段过滤，
 * 逻辑较长且被多个查询复用，故在 XML 中抽成可复用的 SQL 片段
 */
@Mapper
public interface BookInfoMapper {

    /**
     * 分页查询。offset 由上层计算后传入（page 转 offset 在 Service 中完成），
     * categoryId 为 null 时查询全部
     */
    List<BookInfo> selectPage(@Param("offset") int offset,
                              @Param("pageSize") int pageSize,
                              @Param("categoryId") Long categoryId);

    int countByCategory(@Param("categoryId") Long categoryId);

    BookInfo selectById(@Param("id") Long id);

    /**
     * 按书名或作者搜索。「模糊」的表述并不准确：两字及以上走全文索引，
     * 仅单字才退回 LIKE，详见 XML
     */
    List<BookInfo> searchByKeyword(@Param("offset") int offset,
                                   @Param("pageSize") int pageSize,
                                   @Param("keyword") String keyword);

    int countByKeyword(@Param("keyword") String keyword);

    /**
     * 作者名下的作品，按 id 倒序，新书排前面
     */
    List<BookInfo> selectByAuthorId(@Param("authorId") Long authorId);

    int insert(BookInfo bookInfo);

    int updateById(BookInfo bookInfo);

    /**
     * 仅更新 status 一列，用于连载 / 完结切换，其他字段不变
     */
    int updateStatus(BookInfo bookInfo);

    /**
     * 上架状态的唯一写入口，审核流转与管理员下架都走这里
     * auditRemark 传入 null 表示清空（审核通过时用），不留上一次的驳回意见
     */
    int updateAuditStatus(@Param("id") Long id,
                          @Param("auditStatus") Integer auditStatus,
                          @Param("auditRemark") String auditRemark,
                          @Param("updateTime") LocalDateTime updateTime);

    /**
     * 管理端按上架状态查书，分页。审核通过的书用于下架，已下架的书用于恢复
     */
    List<BookInfo> selectByAuditStatus(@Param("auditStatus") Integer auditStatus,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    int countByAuditStatus(@Param("auditStatus") Integer auditStatus);

    int deleteById(@Param("id") Long id);

}
