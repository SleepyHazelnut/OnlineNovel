package com.inkisle.mapper;

import com.inkisle.entity.BookInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BookInfoMapper {

    List<BookInfo> selectPage(@Param("offset") int offset,
                              @Param("pageSize") int pageSize,
                              @Param("categoryId") Long categoryId);

    int countByCategory(@Param("categoryId") Long categoryId);

    /** 不区分上架状态，删分类前用它判断，否则会漏掉未过审和已下架的书 */
    int countAllByCategory(@Param("categoryId") Long categoryId);

    BookInfo selectById(@Param("id") Long id);

    /** 锁作品行，用来把「取最大章节号 + 插入」串起来 */
    Long selectIdForUpdate(@Param("id") Long id);

    List<BookInfo> searchByKeyword(@Param("offset") int offset,
                                   @Param("pageSize") int pageSize,
                                   @Param("keyword") String keyword);

    int countByKeyword(@Param("keyword") String keyword);

    List<BookInfo> selectByAuthorId(@Param("authorId") Long authorId);

    int insert(BookInfo bookInfo);

    int updateById(BookInfo bookInfo);

    int updateStatus(BookInfo bookInfo);

    int updateAuditStatus(@Param("id") Long id,
                          @Param("auditStatus") Integer auditStatus,
                          @Param("auditRemark") String auditRemark,
                          @Param("updateTime") LocalDateTime updateTime);

    int incrementVisitCount(@Param("id") Long id);

    int updateWordCount(@Param("id") Long id, @Param("wordCount") int wordCount);

    /** 首页「最新更新」与更新榜都按 update_time 排序，章节变动后要推一下 */
    int touchUpdateTime(@Param("id") Long id, @Param("updateTime") LocalDateTime updateTime);

    int syncCommentCount(@Param("bookId") Long bookId);

    /** 聚合放在 UPDATE 的子查询里，靠当前读拿到别人已提交的评分 */
    int refreshRating(@Param("bookId") Long bookId);

    List<BookInfo> selectTopByVisit(@Param("top") int top);

    List<BookInfo> selectTopByCreateTime(@Param("top") int top);

    List<BookInfo> selectTopByUpdateTime(@Param("top") int top);

    List<BookInfo> selectByAuditStatus(@Param("auditStatus") Integer auditStatus,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    int countByAuditStatus(@Param("auditStatus") Integer auditStatus);

    int deleteById(@Param("id") Long id);

}
