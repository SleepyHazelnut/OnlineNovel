package com.inkisle.service;

import com.inkisle.entity.BookChapter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface BookChapterService {

    /**
     * 某本书的章节目录，仅含章节信息，不含正文
     */
    List<BookChapter> listByBookId(Long bookId);

    /**
     * 阅读某一章，返回正文及上一章、下一章的 id
     * 读者侧入口，作品未过审时视为不存在
     */
    Map<String, Object> read(Long id);

    /**
     * 作者预览自己的章节
     * 草稿、被驳回的作品均需可读，故此处只校验作者归属，不校验审核状态
     */
    Map<String, Object> readForAuthor(Long id, Long authorId);

    /**
     * 保存章节，章节信息与正文分别写入两张表
     */
    void saveChapter(BookChapter chapter, String content, Long authorId);

    /**
     * 删除章节，正文记录须同步删除，避免产生无关联的残留数据
     */
    void deleteChapter(Long chapterId, Long authorId);

    /**
     * 导入整本 TXT，按标题切分章节，返回本次导入的章节数
     */
    int importChapters(Long bookId, MultipartFile file, Long authorId);

}
