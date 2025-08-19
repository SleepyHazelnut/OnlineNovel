package com.inkisle.search;

import com.inkisle.common.BookAuditStatus;
import com.inkisle.config.SearchProperties;
import com.inkisle.entity.BookInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.MostFields;

/**
 * 走 ES 的书籍搜索
 * 与 MySQL 版本相比，书名字段权重 3、作者名 2、简介 1，命中位置不同得分不同，
 * 排序先按相关度再按阅读数。MySQL 全文索引只能按创建时间排，
 * 搜索「玄幻」时热门作品与冷门作品顺序相同
 * ES 不可用时返回 null，由调用方回落 MySQL。搜索可以慢，但不能失败
 */
@Component
public class BookSearchEngine {

    private static final Logger log = LoggerFactory.getLogger(BookSearchEngine.class);

    @Autowired
    private ElasticsearchOperations operations;

    @Autowired
    private SearchProperties searchProperties;

    /**
     * @return 命中结果，ES 未启用或查询失败时返回 null
     */
    public Map<String, Object> search(String keyword, int pageNum, int pageSize) {
        if (!searchProperties.isEs()) {
            return null;
        }
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.multiMatch(mm -> mm
                                    .query(keyword)
                                    .fields("bookName^3", "authorName^2", "bookDesc")
                                    .type(MostFields)))
                            // 可见性直接按冗余字段过滤，未上架的书根本不在索引里，这条是兜底
                            .filter(f -> f.term(t -> t.field("auditStatus")
                                    .value(BookAuditStatus.PASSED.getCode())))))
                    // 同分时热书在前，避免每次搜出来顺序都飘
                    .withSort(Sort.by(Sort.Order.desc("_score"), Sort.Order.desc("visitCount")))
                    .withPageable(PageRequest.of(pageNum - 1, pageSize))
                    .build();

            SearchHits<BookSearchDoc> hits = operations.search(query, BookSearchDoc.class);
            List<BookInfo> list = hits.getSearchHits().stream()
                    .map(hit -> hit.getContent().toBookInfo())
                    .toList();

            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("total", hits.getTotalHits());
            return data;
        } catch (Exception e) {
            log.warn("ES 查询失败，本次回落到 MySQL 全文索引：keyword={}", keyword, e);
            return null;
        }
    }

}
