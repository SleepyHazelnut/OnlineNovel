package com.inkisle.service;

import com.inkisle.dto.BookSaveDTO;
import com.inkisle.entity.BookInfo;

import java.util.List;
import java.util.Map;

public interface BookService {

    /**
     * 书籍分页列表，`categoryId` 为空时返回全部
     */
    Map<String, Object> page(Integer pageNum, Integer pageSize, Long categoryId);

    BookInfo getById(Long id);

    /**
     * 作者查看自己作品的详情，用于回填编辑表单
     * 此处不校验审核状态：作品被驳回后作者仍需查看原内容才能修改，拦截将导致无法编辑
     */
    BookInfo getForAuthor(Long bookId, Long authorId);

    /**
     * 搜索书籍，书名或作者名命中其一即可
     */
    Map<String, Object> search(Integer pageNum, Integer pageSize, String keyword);

    /**
     * 某个作者名下的全部作品
     */
    List<BookInfo> listByAuthor(Long authorId);

    /**
     * 新增或修改作品，修改时需先校验作品归属该作者
     */
    void save(BookSaveDTO bookSaveDTO, Long authorId);

    /**
     * 删除作品，同样仅可删除本人名下的作品
     */
    void delete(Long id, Long authorId);

    /**
     * 管理员删除作品，不做归属校验
     * 违规作品下架后仍会留在库里，管理端需要一个彻底清理的入口
     */
    void adminDelete(Long id);

    /**
     * 修改连载 / 完结状态
     */
    void updateStatus(Long id, Integer status, Long authorId);

    /**
     * 作品数据：阅读数、收藏数、评论数
     */
    Map<String, Object> stat(Long bookId, Long authorId);

    /**
     * 作品是否对读者可见
     * 判据为「最新一条审核记录为通过」：作品被驳回后修改重交，旧记录仍保留在表中，
     * 若以「是否曾被驳回」为判据，重交并过审的作品将始终不可见
     * 无任何审核记录的作品视为已上架
     */
    boolean isVisibleToReader(Long bookId);

}
