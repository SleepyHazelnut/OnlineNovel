package com.inkisle.service.impl;

import com.inkisle.common.AppClock;
import com.inkisle.common.BusinessException;
import com.inkisle.common.PointBizType;
import com.inkisle.dto.BookVotesDTO;
import com.inkisle.entity.UserInfo;
import com.inkisle.entity.UserPointAccount;
import com.inkisle.entity.UserPointLedger;
import com.inkisle.entity.UserRecommendVote;
import com.inkisle.mapper.UserInfoMapper;
import com.inkisle.mapper.UserPointAccountMapper;
import com.inkisle.mapper.UserPointLedgerMapper;
import com.inkisle.mapper.UserRecommendVoteMapper;
import com.inkisle.service.PointService;
import com.inkisle.service.support.PointLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分账本
 *
 * 三条硬规则，顺序不能换：
 * 1. 幂等靠 (user_id, biz_type, biz_id) 唯一键，不靠「先查再插」
 * 2. 余额靠条件更新，扣不出负数
 * 3. 每日上限先占额度再落账，落账失败要把额度还回去
 */
@Slf4j
@Service
public class PointServiceImpl implements PointService {

    /** 明细每页上限，防止 size 传个十万把库拖死 */
    private static final int MAX_PAGE_SIZE = 50;

    private static final int DEFAULT_PAGE_SIZE = 20;

    /** 荐书榜最多给几本，首页一个板块放得下的量 */
    private static final int MAX_TOP = 50;

    /** 对账一次最多列多少个差异账户 */
    private static final int MAX_RECONCILE = 100;

    /** 每日上限的计数器，跨天靠 TTL 自然失效 */
    private static final String CAP_KEY = "point:cap:%d:%s:%s";

    @Autowired
    private UserPointLedgerMapper ledgerMapper;

    @Autowired
    private UserPointAccountMapper accountMapper;

    @Autowired
    private UserRecommendVoteMapper voteMapper;

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private AppClock clock;

    /**
     * 用户服务有 Redis，但积分不该依赖它可用：
     * 拿不到模板就退回「数今天的流水条数」，慢一点，结论一样
     */
    @Autowired
    private ObjectProvider<StringRedisTemplate> redisProvider;

    @Override
    @Transactional
    public boolean award(Long userId, String bizType, Long bizId, Integer points, String remark) {
        PointBizType type = PointBizType.of(bizType);
        int value = points == null ? type.getPoints() : points;
        if (value <= 0) {
            throw new BusinessException("加分必须为正数");
        }
        if (!oldEnough(userId, type)) {
            return false;
        }
        if (!takeDailyQuota(userId, type)) {
            return false;
        }

        if (ledgerMapper.insertIgnore(newLedger(userId, type, bizId, value, remark)) == 0) {
            // 这条业务已经记过账，把刚占的额度还回去，否则今天的上限会被重复点击吃掉
            giveBackDailyQuota(userId, type);
            return false;
        }
        // 流水是事实，账户是它的物化结果：先有流水再加余额，顺序反了就会多出一笔无来源的余额
        accountMapper.earn(userId, value);
        return true;
    }

    @Override
    @Transactional
    public boolean spend(Long userId, String bizType, Long bizId, int points, String remark) {
        PointBizType type = PointBizType.of(bizType);
        if (points <= 0) {
            throw new BusinessException("扣分必须为正数");
        }
        // 条件更新：够不够和扣没扣在一条语句里，拆开就会扣成负数
        if (accountMapper.deduct(userId, points) == 0) {
            return false;
        }
        if (ledgerMapper.insertIgnore(newLedger(userId, type, bizId, -points, remark)) == 0) {
            // 这笔业务已经扣过。抛出去让事务回滚，刚扣的分跟着退回去
            throw new BusinessException("这本已经投过票了");
        }
        return true;
    }

    @Override
    public Map<String, Object> summary(Long userId) {
        UserPointAccount account = accountMapper.selectByUserId(userId);
        int balance = account == null || account.getBalance() == null ? 0 : account.getBalance();
        int totalEarned = account == null || account.getTotalEarned() == null ? 0 : account.getTotalEarned();

        PointLevel level = PointLevel.of(totalEarned);
        PointLevel next = level.next();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", balance);
        data.put("totalEarned", totalEarned);
        data.put("level", level.getLevel());
        data.put("levelName", level.getName());
        data.put("nextLevelAt", next == null ? null : next.getThreshold());
        data.put("nextLevelName", next == null ? null : next.getName());
        data.put("levelCount", PointLevel.count());
        data.put("todayEarned", ledgerMapper.sumEarnedSince(userId, clock.startOfToday()));
        return data;
    }

    @Override
    public Map<String, Object> ledger(Long userId, Integer pageNum, Integer pageSize) {
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        List<Map<String, Object>> list = new ArrayList<>();
        for (UserPointLedger row : ledgerMapper.selectPage(userId, (page - 1) * size, size)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.getId());
            item.put("bizType", row.getBizType());
            item.put("bizLabel", labelOf(row.getBizType()));
            item.put("points", row.getPoints());
            item.put("remark", row.getRemark());
            item.put("createTime", row.getCreateTime());
            list.add(item);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", ledgerMapper.countByUser(userId));
        data.put("pageNum", page);
        data.put("pageSize", size);
        return data;
    }

    @Override
    public List<Map<String, Object>> rules() {
        List<Map<String, Object>> rules = new ArrayList<>();
        for (PointBizType type : PointBizType.values()) {
            // 连签奖励的分值按天数算，标个区间，别在规则表里写死成 0
            String points = type == PointBizType.SIGN_STREAK
                    ? "3 / 8 / 20"
                    : String.valueOf(type.getPoints());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", type.getCode());
            item.put("label", type.getLabel());
            item.put("points", points);
            item.put("dailyCap", type.getDailyCap());
            item.put("minAccountHours", type.getMinAccountHours());
            rules.add(item);
        }
        return rules;
    }

    @Override
    @Transactional
    public Map<String, Object> recommend(Long userId, Long bookId) {
        if (bookId == null || bookId <= 0) {
            throw new BusinessException("参数不对");
        }
        int cost = -PointBizType.RECOMMEND_VOTE.getPoints();
        // 扣分和记票在同一笔事务里：扣完分却没过票，用户会立刻发现，而且只能手工补
        if (!spend(userId, PointBizType.RECOMMEND_VOTE.getCode(), bookId, cost, "荐书票")) {
            throw new BusinessException("积分不够，再攒攒");
        }
        UserRecommendVote vote = new UserRecommendVote();
        vote.setUserId(userId);
        vote.setBookId(bookId);
        vote.setCost(cost);
        vote.setCreateTime(clock.now());
        voteMapper.insertIgnore(vote);

        Map<String, Object> data = new HashMap<>();
        UserPointAccount account = accountMapper.selectByUserId(userId);
        data.put("balance", account == null ? 0 : account.getBalance());
        data.put("votes", voteMapper.countByBook(bookId));
        return data;
    }

    @Override
    public List<BookVotesDTO> topRecommend(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_TOP));
        return voteMapper.selectTop(size);
    }

    @Override
    public List<Map<String, Object>> reconcile(int limit) {
        int size = Math.max(1, Math.min(limit, MAX_RECONCILE));
        return ledgerMapper.selectMismatch(size);
    }

    private UserPointLedger newLedger(Long userId, PointBizType type, Long bizId, int points, String remark) {
        UserPointLedger ledger = new UserPointLedger();
        ledger.setUserId(userId);
        ledger.setBizType(type.getCode());
        ledger.setBizId(bizId);
        ledger.setPoints(points);
        ledger.setRemark(remark == null ? type.getLabel() : remark);
        ledger.setCreateTime(clock.now());
        return ledger;
    }

    /**
     * 账号太新不给分：注册当天就能批量刷评论、刷阅读进度，
     * 24 小时门槛把「开小号」的成本从「点几下」变成「等一天」
     * 签到不受这个限制，否则新用户第一天进来看不到任何反馈
     */
    private boolean oldEnough(Long userId, PointBizType type) {
        if (type.getMinAccountHours() <= 0) {
            return true;
        }
        UserInfo user = userInfoMapper.selectById(userId);
        if (user == null || user.getCreateTime() == null) {
            return false;
        }
        long hours = Duration.between(user.getCreateTime(), clock.now()).toHours();
        return hours >= type.getMinAccountHours();
    }

    /**
     * 占一个今日额度。返回 false 表示已经用满，这次不给分
     *
     * 用 Redis 计数器而不是每次 COUNT(*)：计数是 O(1)，而这条路径上
     * 每翻一章就要走一次。跨天不靠定时任务清，key 里带日期、TTL 过两天自然过期
     */
    private boolean takeDailyQuota(Long userId, PointBizType type) {
        int cap = type.getDailyCap();
        if (cap <= 0) {
            return true;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            int used = ledgerMapper.countByTypeSince(userId, type.getCode(), clock.startOfToday());
            return used < cap;
        }
        String key = capKey(userId, type, clock.today());
        try {
            Long used = redis.opsForValue().increment(key);
            if (used == null) {
                return true;
            }
            if (used == 1L) {
                redis.expire(key, Duration.ofDays(2));
            }
            if (used > cap) {
                // 超了要先还回去，否则这个 key 会永远比实际多用一格
                redis.opsForValue().decrement(key);
                return false;
            }
            return true;
        } catch (Exception e) {
            // Redis 挂了不能把加分一起打挂，退回数库
            log.warn("积分额度计数失败，退回按流水计数 userId={} type={}", userId, type.getCode(), e);
            return ledgerMapper.countByTypeSince(userId, type.getCode(), clock.startOfToday()) < cap;
        }
    }

    private void giveBackDailyQuota(Long userId, PointBizType type) {
        if (type.getDailyCap() <= 0) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().decrement(capKey(userId, type, clock.today()));
        } catch (Exception e) {
            log.warn("积分额度回补失败 userId={} type={}", userId, type.getCode(), e);
        }
    }

    private static String capKey(Long userId, PointBizType type, LocalDate today) {
        return String.format(CAP_KEY, userId, type.getCode(), today);
    }

    /** 流水的 biz_type 存的是 code，展示时再翻译，历史数据不会因为改文案而对不上 */
    private static String labelOf(String bizType) {
        try {
            return PointBizType.of(bizType).getLabel();
        } catch (BusinessException e) {
            return bizType;
        }
    }

}
