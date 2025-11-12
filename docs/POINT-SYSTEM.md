# 积分系统设计

> 这一份记录的是「为什么这么设计」，不是使用说明。规则和接口会变，这些判断不容易变。
> 实现见 `inkisle-user`（账本）、`inkisle-book`（上报）、`inkisle-common`（共享契约）。

## 一、要不要做

做，但只做两件事：**一个账本 + 一个真出口**。不做商城、签到任务面板、徽章墙、积分排行榜。

理由是站点现状：签到、阅读、评论、评分四个高频动作里，只有签到有反馈（日历上盖个章），
其余三个做完就结束了。用户攒三天和攒三十天看到的界面完全一样，行为和结果之间没有连线。
积分补的就是这条线。

反过来，一旦加上商城、任务、徽章，就要维护商品、库存、订单、发货状态——那是电商的复杂度，
落到一个阅读站上，用户不会为了这些留下，维护成本却是真的。**一个出口足够证明这条线是通的。**

## 二、规则

| 行为 | biz_type | 分值 | 频次上限 | 账号龄门槛 |
|---|---|---|---|---|
| 每日签到 | `SIGN` | +2 | 每天 1 次 | 无 |
| 连签 3 / 7 / 30 天 | `SIGN_STREAK` | +3 / +8 / +20 | 每个里程碑一次 | 无 |
| 读完一章 | `READ_CHAPTER` | +1 | 每天 20 章 | 24 小时 |
| 评分 | `RATING` | +3 | 每本第一次 | 24 小时 |
| 评论过审 | `COMMENT_PASS` | +5 | 每天 3 条 | 24 小时 |
| 荐书票（消耗） | `RECOMMEND_VOTE` | −20 | 每本一次 | 24 小时 |

规则写死在 `PointBizType` 枚举里，不放配置中心。分值就六个，改一次要发版，代价远低于
「线上行为和代码里算的不是一回事」的排查成本。真到运营要随手调价，再挪也不迟。

三条设计取舍：

- **签到不看账号龄，其余都看**。否则新用户注册当天做任何事都没有反馈，第一天就走了。
- **上限按来源分开计**。阅读一天 20 章、评论一天 3 条，用同一个总额度会让两个行为互相挤占。
- **消耗只有荐书票**。票数进首页「读者荐书」板块，是唯一有外部性的出口——花出去的分能改变别人看到什么。

等级看**累计获得**，不看余额：

| 等级 | 称号 | 门槛（累计获得） |
|---|---|---|
| 1 | 初读 | 0 |
| 2 | 常读 | 60 |
| 3 | 书友 | 200 |
| 4 | 书痴 | 600 |
| 5 | 藏书家 | 1500 |

看余额的话，用户一兑换就掉级，于是没人敢花，积分变成只进不出的死数——这是「死积分」最常见的成因。

## 三、数据模型

三张表，都在 `inkisle` 库、都归 `inkisle-user`。

```sql
user_point_ledger   -- 流水，只增
  UNIQUE uk_user_biz (user_id, biz_type, biz_id)
  KEY idx_user_time (user_id, create_time)
  KEY idx_user_type_time (user_id, biz_type, create_time)

user_point_account  -- 余额，流水的物化结果
  PRIMARY KEY (user_id)
  balance / total_earned / update_time

user_recommend_vote -- 荐书票
  UNIQUE uk_user_book (user_id, book_id)
```

### 三个关键决策

**一、账本放 user 服务，不放 book。**

`inkisle-user/pom.xml` 里没有 openfeign。账本放 book 就要让 book → user → book 成环。
更要紧的是：五个服务连的是同一个 MySQL 实例，`book` 的本地事务持着 `book_info` 行锁，
再同步 Feign 回来写同一行，MySQL 检测不到跨进程的死锁，只能等 `innodb_lock_wait_timeout` 50 秒。
编辑作品那条链路已经踩过一次（见 `OPTIMIZATION-BACKLOG.md` P0-1），账本不能再造一个同样的入口。

**二、余额单独一张表，不塞 `user_info`。**

`user_info` 上已经有 `updateStatus(UserInfo)` 这种拿半对象整行覆盖的写法，加一列进去，
早晚在一次「顺手改状态」里被静默抹掉。余额和累计值是账务数据，得有自己的行。

**三、幂等靠唯一键，不靠「先查再插」。**

`(user_id, biz_type, biz_id)` 唯一：
- 读同一章 → `biz_id = chapterId`，一辈子只算一次
- 签到 → `biz_id = today.toEpochDay()`，一天一条
- 评分 → `biz_id = bookId`，改分不再给
- 评论 → `biz_id = commentId`，过审才写

写库用 `INSERT IGNORE`，命中唯一键返回 0 行，直接当幂等判据。
这里踩过一个坑：起初用的是 `ON DUPLICATE KEY UPDATE id = id`，看着等价，实际 **MySQL 驱动默认带
`CLIENT_FOUND_ROWS`，这种写法在「命中但值没变」时返回 1 不是 0**，幂等判据直接失效，
两笔重复流水都记上了。`INSERT IGNORE` 才是稳定返回 0 的。

## 四、并发

**扣减**：余额判断写进 `WHERE`，让「够不够」和「扣没扣」在同一个原子操作里完成。

```sql
UPDATE user_point_account
SET balance = balance - #{points}
WHERE user_id = #{userId} AND balance >= #{points}
```

影响 0 行就是余额不够，不存在扣成负数的窗口。扣完分再插流水；流水插不进去（重复业务）
就抛异常让事务回滚，刚扣的分跟着退回去。

**每日上限**：Redis 计数器 `point:cap:{userId}:{bizType}:{日期}`，`INCR` 之后判断是否超限。
用 `COUNT(*)` 也能算，但这条路径上每翻一章就要走一次，计数是 O(1)。跨天不靠定时任务清，
key 里带日期、TTL 两天自然过期。Redis 不可用时退回数库，结论一样，只是慢。

## 五、链路

```
签到       user:SignServiceImpl ──► PointService.award（本地事务，无跨服务调用）
阅读一章   book:ReadHistoryController ──► PointReporter.awardAfterCommit ──► Feign ──► user:/internal/point/award
评分       book:BookRatingServiceImpl ──► PointReporter.awardAfterCommit ──► …
评论过审   book:BookCommentServiceImpl.pass ──► PointReporter.awardAfterCommit ──► …
荐书票     user:PointServiceImpl.recommend（扣分 + 记流水 + 记票，一笔本地事务）
首页荐书   book:HomeBookServiceImpl ──► Feign ──► user:/internal/point/top-recommend
```

上报统一走 `PointReporter`，它做两件事：

1. **事务提交后才发**。在事务里发同步调用，持锁时间等于一次 HTTP 往返。
2. **失败只记 warn 日志，不抛**。积分是旁路，用户服务抖一下不该让「评分失败」「进度没保存」。

也就是说：积分可能丢，但主流程不会因为积分挂掉。丢的分对账发现不了（流水根本没写），
所以那条日志必须留。

## 六、对账

`GET /admin/point/reconcile`，SQL 在 `UserPointLedgerMapper.xml`：

```sql
SELECT l.user_id, l.total AS ledgerSum, l.earned AS ledgerEarned,
       COALESCE(a.balance, 0) AS balance, COALESCE(a.total_earned, 0) AS totalEarned
FROM (SELECT user_id, SUM(points) AS total,
             SUM(CASE WHEN points > 0 THEN points ELSE 0 END) AS earned
      FROM user_point_ledger GROUP BY user_id) l
LEFT JOIN user_point_account a ON a.user_id = l.user_id
WHERE COALESCE(a.balance, 0) <> l.total
   OR COALESCE(a.total_earned, 0) <> l.earned
```

以流水为准去比账户：流水是事实，账户只是它的物化结果。空列表才是正常。

**不做自动修正**。自动修正等于用一个猜测覆盖另一个猜测，真因会被永久掩盖。

## 七、顺带修掉的三个现有 bug

这三个和积分无关，但接积分之前必须补上，否则第一天就会有人刷。

1. **`SignServiceImpl` 三处 `LocalDate.now()` 没带时区**。容器默认 UTC，改一次 `TZ`，
   北京时间 00:00 和 08:01 会落进两个「今天」，一天签两次、拿两份连续奖励。
   现在统一走 `AppClock`，时区是配置项，不跟着部署环境走。
2. **`RatingController` 没有 `@RepeatSubmit`**，而 `rate()` 是 upsert 覆盖，循环提交就能刷。
3. **`POST /history/save` 的 `chapterId` 完全信任请求体**，只校验 `bookId` 可见性，
   而 `UserReadHistoryServiceImpl.save` 无条件 `addHot`——**能直接刷热榜**。
   现在章节归属与审核状态都校验，章节号以库里为准，热度只在「真的换了一章」时才加。

## 八、这套设计什么时候撑不住

- **日活上万**：`top-recommend` 每次首页都 `GROUP BY book_id` 全表扫票表，要加缓存或物化票数。
  `countByBook` 也一样。
- **要对历史订单式退款**：现在流水是「终态记账」，没有冲正。要支持退还票、扣回违规所得的积分，
  得加正负冲正流水并放开 `uk_user_biz`，那是另一套设计。
- **多库分片**：`(user_id, biz_type, biz_id)` 的唯一性依赖同库，分片后要换成分片键 + 全局去重表。

## 九、三个容易写错的地方

1. 幂等判据取 `INSERT IGNORE` 的 0 行，不用 `ON DUPLICATE KEY UPDATE`：驱动默认带
   `CLIENT_FOUND_ROWS`，后者命中唯一键但值未变时返回的是 1 而不是 0，判据随即失效。
2. 跨服务上报放在 `afterCommit` 里发，失败只记日志：本地事务持锁的时间不能等于一次 HTTP 往返。
3. 余额用条件更新扣减，`WHERE balance >= ?` 保证扣不出负数；余额与流水求和之间的偏差
   由对账接口比对得出，不做自动修正。
