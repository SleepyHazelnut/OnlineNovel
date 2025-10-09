package com.inkisle.mapper;

import com.inkisle.entity.BookContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BookContentMapper {

    /**
     * 按章节 ID 查询正文。正文单独建表，避免大字段拖慢章节列表查询
     */
    BookContent selectByChapterId(@Param("chapterId") Long chapterId);

    int insert(BookContent content);

    /** 整本导入用：章节批量插入后按回填的 id 一次写入全部正文 */
    int insertBatch(@Param("contents") java.util.List<BookContent> contents);

    // 正文和章节一对一，chapter_id 就能定位，下面几个方法都用它当条件
    int updateByChapterId(BookContent content);

    int deleteByChapterId(@Param("chapterId") Long chapterId);

    /**
     * 删一本书所有章节的正文，删书的时候一起清
     * 不清的话这些字谁也读不到，还一直占地方
     */
    int deleteByBookId(@Param("bookId") Long bookId);

}
