package com.inkisle.mapper;

import com.inkisle.entity.BookInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
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

    /**
     * 该分类下的作品数，不区分上架状态
     * 删除分类前须用它判断：`countByCategory` 只数已上架作品，
     * 用那个判断会漏掉未过审与已下架的书，删完分类这些书就指向了不存在的分类
     */
    int countAllByCategory(@Param("categoryId") Long categoryId);

    BookInfo selectById(@Param("id") Long id);

    /**
     * 锁住作品行，仅取 id
     * 用于把「读当前最大章节号 + 插入下一章」串成串行操作：
     * 不加锁时两个并发的追加请求会读到同一个最大值，插入同一个章节号
     */
    Long selectIdForUpdate(@Param("id") Long id);

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
     * 阅读次数加一。每打开一章调用一次，直接写库，不做额外缓冲
     */
    int incrementVisitCount(@Param("id") Long id);

    /**
     * 覆盖总字数，章节增删改后按正文汇总重算
     */
    int updateWordCount(@Param("id") Long id, @Param("wordCount") int wordCount);

    /**
     * 评论数按评论表重算。增删评论后调用，避免逐次加减导致与实际数据不一致
     */
    int syncCommentCount(@Param("bookId") Long bookId);

    /**
     * 覆盖平均分与评分人数，由评分表聚合后传入
     */
    int updateRating(@Param("id") Long id,
                     @Param("score") BigDecimal score,
                     @Param("ratingCount") int ratingCount);

    /**
     * 点击榜：已上架作品中阅读次数最多的前 N 本
     */
    List<BookInfo> selectTopByVisit(@Param("top") int top);

    /**
     * 新书榜：按创建时间倒序
     */
    List<BookInfo> selectTopByCreateTime(@Param("top") int top);

    /**
     * 更新榜：按最近更新时间倒序，有新章节的书会排上来
     */
    List<BookInfo> selectTopByUpdateTime(@Param("top") int top);

    /**
     * 管理端按上架状态查书，分页。审核通过的书用于下架，已下架的书用于恢复
     */
    List<BookInfo> selectByAuditStatus(@Param("auditStatus") Integer auditStatus,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    int countByAuditStatus(@Param("auditStatus") Integer auditStatus);

    int deleteById(@Param("id") Long id);

}
