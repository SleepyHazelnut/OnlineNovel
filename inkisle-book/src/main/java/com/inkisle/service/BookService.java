package com.inkisle.service;

import com.inkisle.dto.BookSaveDTO;
import com.inkisle.entity.BookInfo;

import java.util.List;
import java.util.Map;

public interface BookService {

    Map<String, Object> page(Integer pageNum, Integer pageSize, Long categoryId);

    BookInfo getById(Long id);

    /** 回填编辑表单用，不校验审核状态：被驳回的书也要能打开改 */
    BookInfo getForAuthor(Long bookId, Long authorId);

    Map<String, Object> search(Integer pageNum, Integer pageSize, String keyword);

    List<BookInfo> listByAuthor(Long authorId);

    void save(BookSaveDTO bookSaveDTO, Long authorId);

    /** 作者删除自己的作品，关联数据一并清理 */
    void delete(Long id, Long authorId);

    /** 管理端删除，不做归属校验 */
    void adminDelete(Long id);

    void updateStatus(Long id, Integer status, Long authorId);

    Map<String, Object> stat(Long bookId, Long authorId);

    /** 只看 book_info.audit_status，不回查审核记录表 */
    boolean isVisibleToReader(Long bookId);

}
