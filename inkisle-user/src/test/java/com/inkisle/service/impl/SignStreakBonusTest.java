package com.inkisle.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 连签奖励的分值
 * 只有刚好签满 3/7/30 天那次给，第 4 天不给、第 8 天也不重复给第 7 天的
 */
class SignStreakBonusTest {

    @Test
    @DisplayName("签满 3、7、30 天当天才给奖励")
    void bonusOnlyOnMilestones() {
        assertThat(SignServiceImpl.streakBonus(3)).isEqualTo(3);
        assertThat(SignServiceImpl.streakBonus(7)).isEqualTo(8);
        assertThat(SignServiceImpl.streakBonus(30)).isEqualTo(20);
    }

    @Test
    @DisplayName("里程碑之外的天数一律不给")
    void noBonusBetweenMilestones() {
        for (int days : new int[]{1, 2, 4, 6, 8, 29, 31, 100}) {
            assertThat(SignServiceImpl.streakBonus(days)).isZero();
        }
    }

}
