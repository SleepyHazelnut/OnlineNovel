package com.inkisle.service.support;

import com.inkisle.client.PointClient;
import com.inkisle.common.PointBizType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 把加分上报给用户服务
 *
 * 两件事必须做对，否则这个旁路会把主流程拖垮：
 *
 * 一、上报放在事务提交之后。服务之间连的是同一个库，在本地事务里发同步调用，
 * 持锁时间等于一次 HTTP 往返，慢一点就把别的事务卡在行锁上（编辑作品那条链路踩过）。
 *
 * 二、失败只记日志，不往上抛。积分是激励，不是业务事实：
 * 用户服务抖一下就让「评分失败」「阅读进度没保存」，是拿次要功能换主要功能
 */
@Slf4j
@Component
public class PointReporter {

    @Autowired
    private PointClient pointClient;

    /**
     * 加分，事务提交后执行
     *
     * @param points 为空时用来源的默认分值
     */
    public void awardAfterCommit(Long userId, PointBizType type, Long bizId, Integer points, String remark) {
        if (userId == null || bizId == null) {
            // 游客评分、匿名阅读这类情况没有加分对象，直接跳过，不算异常
            return;
        }
        Runnable task = () -> award(userId, type, bizId, points, remark);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }

    private void award(Long userId, PointBizType type, Long bizId, Integer points, String remark) {
        try {
            pointClient.award(userId, type.getCode(), bizId, points, remark);
        } catch (Exception e) {
            // 加分失败不回滚主流程，也不重试：唯一键保证重试安全，但重试会把
            // 一次抖动放大成连续失败。丢的分由对账发现不了（流水根本没写），
            // 所以这里必须留一条能搜到的日志
            log.warn("积分上报失败 userId={} type={} bizId={}", userId, type.getCode(), bizId, e);
        }
    }

}
