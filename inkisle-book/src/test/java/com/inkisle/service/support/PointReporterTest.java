package com.inkisle.service.support;

import com.inkisle.client.PointClient;
import com.inkisle.common.PointBizType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分上报是旁路：它挂掉不能往上抛，否则用户那次评分、评论会跟着失败
 */
class PointReporterTest {

    private PointClient pointClient;

    private PointReporter pointReporter;

    @BeforeEach
    void setUp() {
        pointClient = Mockito.mock(PointClient.class);
        pointReporter = new PointReporter();
        ReflectionTestUtils.setField(pointReporter, "pointClient", pointClient);
    }

    @Test
    @DisplayName("没有事务时直接上报")
    void callsClientWithoutTransaction() {
        pointReporter.awardAfterCommit(1L, PointBizType.RATING, 2L, null, "评了个分");

        verify(pointClient).award(1L, PointBizType.RATING.getCode(), 2L, null, "评了个分");
    }

    @Test
    @DisplayName("上报失败只写日志，不把异常抛给调用方")
    void swallowsClientFailure() {
        when(pointClient.award(anyLong(), any(), anyLong(), any(), any()))
                .thenThrow(new RuntimeException("user 服务挂了"));

        assertThatCode(() -> pointReporter.awardAfterCommit(1L, PointBizType.COMMENT_PASS, 3L, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("没有加分对象时不上报，游客评分不该报错")
    void skipsWhenUserIdMissing() {
        pointReporter.awardAfterCommit(null, PointBizType.RATING, 2L, null, null);
        pointReporter.awardAfterCommit(1L, PointBizType.RATING, null, null, null);

        verify(pointClient, never()).award(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("在事务里注册成提交后执行，不占着本地事务等 HTTP")
    void defersUntilAfterCommit() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            pointReporter.awardAfterCommit(9L, PointBizType.READ_CHAPTER, 8L, null, "第 8 章");

            // 事务还没提交，一次 HTTP 都不该发出去
            verify(pointClient, never()).award(eq(9L), any(), eq(8L), any(), any());

            for (var synchronization
                    : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(pointClient).award(9L, PointBizType.READ_CHAPTER.getCode(), 8L, null, "第 8 章");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

}
