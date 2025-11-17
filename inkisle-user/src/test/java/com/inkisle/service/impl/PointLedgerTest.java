package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.PointBizType;
import com.inkisle.service.PointService;
import com.inkisle.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 积分账本
 * 幂等、余额不为负、等级口径这三件事全在 SQL 里，mock 掉 mapper 就测不出来了
 */
@Transactional
class PointLedgerTest extends BaseIntegrationTest {

    /** 用户名每次跑都要不同，测试库不清表，重名会撞唯一键 */
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 100_000);

    @Autowired
    private PointService pointService;

    @Test
    @DisplayName("同一条来源重复上报只记一次账")
    void awardIsIdempotent() {
        Long userId = oldUser();
        String type = PointBizType.READ_CHAPTER.getCode();

        assertThat(pointService.award(userId, type, 9001L, null, "第 1 章")).isTrue();
        assertThat(pointService.award(userId, type, 9001L, null, "第 1 章")).isFalse();

        Map<String, Object> summary = pointService.summary(userId);
        assertThat(summary.get("balance")).isEqualTo(1);
        assertThat(summary.get("totalEarned")).isEqualTo(1);
        assertThat(countBy("user_point_ledger", "user_id", userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("余额不够时扣不动，也不会扣成负数")
    void balanceNeverGoesNegative() {
        Long userId = oldUser();
        pointService.award(userId, PointBizType.READ_CHAPTER.getCode(), 9002L, 3, "第 2 章");

        assertThat(pointService.spend(userId, PointBizType.RECOMMEND_VOTE.getCode(), 1L, 20, "荐书票")).isFalse();

        assertThat(pointService.summary(userId).get("balance")).isEqualTo(3);
        // 扣分失败不该留下流水
        assertThat(countBy("user_point_ledger", "user_id", userId)).isEqualTo(1);
    }

    /**
     * 这个方法故意不套测试事务。
     * 套上的话 spend 的 REQUIRED 会并进测试事务，抛异常只是把整个测试事务标成
     * rollback-only，扣掉的分要等测试结束才回滚，方法里读到的还是扣过的余额。
     * 不套事务才是线上真实的调用形态：一次调用一笔事务，失败就当场回滚
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("同一笔业务重复扣分抛业务异常，已经扣掉的分回滚")
    void duplicateSpendRollsBack() {
        Long userId = oldUser();
        pointService.award(userId, PointBizType.READ_CHAPTER.getCode(), 9003L, 50, null);
        String vote = PointBizType.RECOMMEND_VOTE.getCode();

        assertThat(pointService.spend(userId, vote, 77L, 20, "荐书票")).isTrue();
        assertThatThrownBy(() -> pointService.spend(userId, vote, 77L, 20, "荐书票"))
                .isInstanceOf(BusinessException.class);

        // 第二次扣的 20 分必须退回去，否则用户花一次钱被扣两次
        assertThat(pointService.summary(userId).get("balance")).isEqualTo(30);
    }

    @Test
    @DisplayName("等级看累计获得，不因为花分掉级")
    void levelFollowsTotalEarned() {
        Long userId = oldUser();
        pointService.award(userId, PointBizType.READ_CHAPTER.getCode(), 9004L, 70, null);

        Map<String, Object> before = pointService.summary(userId);
        assertThat(before.get("level")).isEqualTo(2);

        pointService.spend(userId, PointBizType.RECOMMEND_VOTE.getCode(), 5L, 60, "荐书票");

        Map<String, Object> after = pointService.summary(userId);
        assertThat(after.get("balance")).isEqualTo(10);
        // 花掉 60 分，累计值仍是 70，等级不该跟着掉
        assertThat(after.get("totalEarned")).isEqualTo(70);
        assertThat(after.get("level")).isEqualTo(2);
    }

    @Test
    @DisplayName("注册当天不给阅读分，签到照给")
    void newAccountGetsNoReadingPoints() {
        Long userId = freshUser(0);

        assertThat(pointService.award(userId, PointBizType.READ_CHAPTER.getCode(), 9005L, null, null)).isFalse();
        // 签到没有账号龄门槛，否则新用户第一天进来什么反馈都看不到
        assertThat(pointService.award(userId, PointBizType.SIGN.getCode(), 9006L, null, null)).isTrue();
    }

    @Test
    @DisplayName("账号满一天后阅读分正常发放")
    void oldAccountGetsReadingPoints() {
        Long userId = freshUser(0);
        jdbcTemplate.update("UPDATE user_info SET create_time = DATE_SUB(NOW(), INTERVAL 3 DAY) WHERE id = ?", userId);

        assertThat(pointService.award(userId, PointBizType.READ_CHAPTER.getCode(), 9007L, null, null)).isTrue();
    }

    @Test
    @DisplayName("阅读分有每日上限，超出的部分不再给")
    void readingPointsAreCappedPerDay() {
        Long userId = freshUser(0);
        jdbcTemplate.update("UPDATE user_info SET create_time = DATE_SUB(NOW(), INTERVAL 3 DAY) WHERE id = ?", userId);

        int cap = PointBizType.READ_CHAPTER.getDailyCap();
        int awarded = 0;
        for (long chapterId = 10_000; chapterId < 10_000 + cap + 5; chapterId++) {
            if (pointService.award(userId, PointBizType.READ_CHAPTER.getCode(), chapterId, null, null)) {
                awarded++;
            }
        }

        assertThat(awarded).isEqualTo(cap);
        assertThat(countBy("user_point_ledger", "user_id", userId)).isEqualTo(cap);
    }

    @Test
    @DisplayName("对账能发现余额与流水求和不等")
    void reconcileFindsMismatch() {
        Long userId = oldUser();
        pointService.award(userId, PointBizType.READ_CHAPTER.getCode(), 9008L, 40, null);

        // 手工把余额改错，模拟「一边写成功一边没写」
        jdbcTemplate.update("UPDATE user_point_account SET balance = balance + 7 WHERE user_id = ?", userId);

        List<Map<String, Object>> mismatch = pointService.reconcile(100);
        Map<String, Object> mine = mismatch.stream()
                .filter(row -> Long.valueOf(userId).equals(asLong(row.get("userId"))))
                .findFirst()
                .orElse(null);

        assertThat(mine).isNotNull();
        assertThat(asLong(mine.get("balance"))).isEqualTo(47);
        assertThat(asLong(mine.get("ledgerSum"))).isEqualTo(40);
    }

    @Test
    @DisplayName("明细分页按时间倒序，消耗显示为负数")
    void ledgerListsNewestFirst() {
        Long userId = oldUser();
        pointService.award(userId, PointBizType.READ_CHAPTER.getCode(), 9009L, 30, "第 9 章");
        pointService.spend(userId, PointBizType.RECOMMEND_VOTE.getCode(), 9L, 20, "荐书票");

        Map<String, Object> page = pointService.ledger(userId, 1, 10);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) page.get("list");

        assertThat(page.get("total")).isEqualTo(2);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("points")).isEqualTo(-20);
        assertThat(list.get(0).get("bizLabel")).isEqualTo("荐书票");
        assertThat(list.get(1).get("points")).isEqualTo(30);
    }

    private Long freshUser(int status) {
        return insertUser("point" + SEQ.incrementAndGet(), status);
    }

    /**
     * 注册满三天的账号
     * 阅读、评分、评论三条来源都有 24 小时账号龄门槛，拿刚注册的账号测它们只会测到「不达标」
     */
    private Long oldUser() {
        Long userId = insertUser("point" + SEQ.incrementAndGet(), 0);
        jdbcTemplate.update("UPDATE user_info SET create_time = DATE_SUB(NOW(), INTERVAL 3 DAY) WHERE id = ?", userId);
        return userId;
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

}
