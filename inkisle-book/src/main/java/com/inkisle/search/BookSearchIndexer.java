package com.inkisle.search;

import com.inkisle.common.BookAuditStatus;
import com.inkisle.config.SearchProperties;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookInfoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 维护书籍索引
 * 写库之后调 sync(bookId)，由它回表取最新状态决定写入还是删除，
 * 调用方无需区分新增、改名或下架
 * 在事务内调用时推迟到提交之后，避免库回滚后索引残留脏数据
 * 索引写失败只记日志不抛异常，搜索故障不能连带影响发书。出问题用管理端全量重建
 */
@Component
public class BookSearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(BookSearchIndexer.class);

    /** 重建时一批取多少，取大点少跑几趟 */
    private static final int REBUILD_BATCH = 200;

    @Autowired
    private ElasticsearchOperations operations;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private SearchProperties searchProperties;

    /**
     * 同步一本书，能看见的写进索引，看不见或者已经删掉的从索引里拿掉
     */
    public void sync(Long bookId) {
        if (!searchProperties.isEs() || bookId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSync(bookId);
                }
            });
            return;
        }
        doSync(bookId);
    }

    private void doSync(Long bookId) {
        try {
            BookInfo book = bookInfoMapper.selectById(bookId);
            if (book == null || !BookAuditStatus.isVisible(book.getAuditStatus())) {
                // 未上架的书不进索引：读者搜不到，也不必每次搜索都靠过滤条件兜底
                operations.delete(String.valueOf(bookId), BookSearchDoc.class);
                return;
            }
            ensureIndex();
            operations.save(BookSearchDoc.from(book));
        } catch (Exception e) {
            log.warn("同步书籍索引失败，bookId={}，搜索将暂时查不到这本书", bookId, e);
        }
    }

    /**
     * 全量重建：删除索引后按映射重建，再导入已上架作品
     * 平时不用，更换实例或索引损坏时执行
     *
     * @return 写入条数
     */
    public int rebuild() {
        IndexOperations indexOps = operations.indexOps(BookSearchDoc.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.createWithMapping();

        int offset = 0;
        int total = 0;
        while (true) {
            List<BookInfo> batch = bookInfoMapper.selectByAuditStatus(
                    BookAuditStatus.PASSED.getCode(), offset, REBUILD_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            batch.forEach(book -> operations.save(BookSearchDoc.from(book)));
            total += batch.size();
            if (batch.size() < REBUILD_BATCH) {
                break;
            }
            offset += REBUILD_BATCH;
        }
        log.info("书籍索引重建完成，共 {} 本", total);
        return total;
    }

    /** 索引不存在时自动创建，无需手动执行脚本 */
    private void ensureIndex() {
        IndexOperations indexOps = operations.indexOps(BookSearchDoc.class);
        if (!indexOps.exists()) {
            indexOps.createWithMapping();
        }
    }

}
