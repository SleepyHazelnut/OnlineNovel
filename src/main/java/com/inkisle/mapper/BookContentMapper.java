package com.inkisle.mapper;

import com.inkisle.entity.BookContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BookContentMapper {

    /**
     * 按章节ID取正文
     */
    BookContent selectByChapterId(@Param("chapterId") Long chapterId);

}
