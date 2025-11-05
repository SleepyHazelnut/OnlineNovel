# 墨屿小说 · 逻辑优化清单

> 来源：按书籍与阅读链路、账号与鉴权、跨服务与数据模型、前端读者端四条线分别审查，
> 交叉去重后 38 条分级、11 条零碎。标注「已复核」的是逐条回读代码确认过的，其余为静态阅读结论。
>
> 分档逻辑：**P0 = 现在就会挂的 bug**，P1 = 一致性与安全缺陷，P2 = 规模上来才疼的性能问题，P3 = 前端体验。

---

## P0 · 会挂的 bug（优先修，演示时必现）

### P0-1 编辑已上架作品必现 50 秒锁等待后 500【已复核】

- 位置：`inkisle-book/.../service/impl/BookServiceImpl.java:174,217-224`、`inkisle-audit/.../BookAuditServiceImpl.java:224-229`
- 现状：`save()` 是 `@Transactional`，先 `bookInfoMapper.updateById` 拿到 book_info 的行锁，然后同步发 Feign：
  `auditClient.resubmitAfterEdit` → 审核服务 `markBook` → 又 Feign 回调
  `POST /internal/book/{id}/audit-status` → 书籍服务另一个事务 `UPDATE book_info WHERE id=?` 同一行。
- 后果：内层等外层提交，外层在等 HTTP 响应，MySQL 检测不到死锁，只能等 `innodb_lock_wait_timeout`（默认 50s）报错。
  改书名/简介这类最常见操作必现。
- 改法：`resubmitAfterEdit` 挪进 `TransactionSynchronization.afterCommit`（同 `removeAuditRecordsAfterCommit` 的写法）；
  更彻底是让审核服务只写 `book_audit_record`，`book_info.audit_status` 由书籍服务自己用 `markPendingIfNotPending` 幂等更新，砍掉 `book → audit → book` 这个回写环。
- 成本：小

### P0-2 `/internal/**` 三处裸奔，直连服务端口即可改审核状态【已复核】

- 位置：`inkisle-user/.../config/WebMvcConfig.java:27`、`inkisle-book/.../WebMvcConfig.java:25`、`inkisle-audit/.../WebMvcConfig.java:22`
- 现状：三个服务的拦截器都 `excludePathPatterns("/internal/**")`，且无任何服务间凭证。服务监听 `0.0.0.0:808x`。
- 后果：`curl -X POST http://host:8082/internal/book/12/audit-status -d '{"status":1}'` 就能把自己的书置为过审；
  `DELETE /internal/audit/book/1` 能删任意书的审核记录。「网关不转发 /internal」是假设，不是控制。
- 改法：`inkisle-common` 加 `InternalAuthFilter`，校验 `X-Internal-Token`（`MessageDigest.isEqual` 常量时间），缺省启动失败；
  Feign 侧 `RequestInterceptor` 统一注入；网关 `RemoveRequestHeader=X-Internal-Token` 防走私。
- 成本：中

### P0-3 前端在网关 `/app` 子路径下整站路由失效【已复核】

- 位置：`web/src/router/index.js:44`、`web/vite.config.js:35`
- 现状：`base: forGateway ? '/app/' : '/'`，但 `createWebHistory()` 无参，`index.html` 也没有 `<base>`。
  构建产物里就是 `history: qc()`。
- 后果：打开 `http://localhost:8080/app/` 被 `/:pathMatch(.*)*` 兜底成 404，任何导航都跳到网关根。
  README 写的入口正是这个地址。
- 改法：`createWebHistory(import.meta.env.BASE_URL)`，并统一 `client.js` 的登录跳转、401 回跳、`logout` 三处前缀。
  网关回退只覆盖 `/app/**`，登录后的 `redirect` 不带前缀就刷新即 404。
- 成本：小

### P0-4 「我的评论」恒空白（接口契约对不上）【已复核】

- 位置：`web/src/api/index.js:26`、`web/src/views/MeView.vue:62,315`
- 现状：后端 `pageByUser` 返回 `{list, total}`，前端 `state.comments = await commentApi.mine() || []`，
  然后 `v-if="!state.comments.length"` —— 对象没有 `length`，`undefined` 恒假。
- 后果：任何有评论的用户都看到空态。
- 改法：接口层接 `pageNum/pageSize`，`state.comments = data?.list || []`，接入现成的 `Pager.vue`。
- 成本：小

### P0-5 评分星级换算错了一倍【已复核】

- 位置：`web/src/views/BookView.vue:38`
- 现状：`Math.round(Number(score) / 2)`，但后端 `ROUND(AVG(score),1)` 的 score 本来就是 0–5，`RatingDTO` 也是 `@Min(1) @Max(5)`。
- 后果：4.5 分显示 2 颗星，和旁边 `scoreText` 的「4.5」自相矛盾。
- 改法：去掉 `/2`；要细腻就用半星（`is-half` + 渐变裁切）。
- 成本：小

### P0-6 整本 TXT 导入用标题做 Map key，同名章节静默丢正文

- 位置：`inkisle-book/.../util/TxtChapterParser.java:25,36,44`，消费方 `BookChapterServiceImpl.java:188`
- 现状：`Map<String,String> chapters` + `chapters.put(lastTitle, ...)`，分卷重复/作者笔误的同名标题互相覆盖。
  返回给前端的章节数取自 `chapters.size()`，少了也不报错。
- 后果：唯一会造成**数据丢失**的 bug，用户无法察觉。
- 改法：改成 `List<ChapterSlice>` 有序切片，顺带消掉 `rows`/`contents` 两个平行 List 的下标对齐风险；补一个重名标题的单测。
- 成本：小

---

## P1 · 一致性与安全

### P1-1 缓存失效写在事务内，并发读会把旧值灌回去

- 位置：`BookChapterServiceImpl.java:124,253,266-267`、`BookCommentServiceImpl.java:70,205-209`、`BookRatingServiceImpl.java:42,84-90`
- 现状：注释写「先写库再删缓存」，防的是删早了；但没防「提交晚」——
  T1 提交前，T2 未命中 → 读到 T1 未提交的旧行 → `CacheAside` 把旧值 `put` 回去 → T1 提交，缓存脏到 TTL（正文 2h）。
- 这说明「缓存三件套」还留了一个窗口没堵上，P1-1 的改法补的就是它。
- 改法：抽 `CacheInvalidator.evictAfterCommit(name, key)`，`isSynchronizationActive()` 为真则注册 `afterCommit`，否则立即删；异常只 warn。
- 成本：小

### P1-2 JWT 没有吊销：登出、改密、封禁都要等 24 小时

- 位置：`inkisle-common/.../util/JwtUtil.java:35-46`、`inkisle-user/.../UserServiceImpl.java:166-178`
- 现状：token 只有 `sub/username/role`，无 `jti`、无版本号、无 `/logout`；`changePassword` 只改库。
  `role` 是签发时快照，改权也不生效。
- 改法（比黑名单省内存）：Redis 存 `auth:ver:{role}:{id}`，签发时写 claim `ver`，拦截器比对不一致即 401；
  改密/封禁/停用/登出都 `INCR`。管理员 token 单独降到 2h 配一次性 refresh。
- 成本：中

### P1-3 封禁只在书籍服务生效，账号侧有 24 小时空窗

- 位置：`inkisle-user/.../interceptor/LoginInterceptor.java:16-18,44-51` vs `inkisle-book/.../LoginInterceptor.java:56-59`
- 现状：book 侧查了 `UserStatusCache`，user 侧注释写着「不查库，最多再用 24 小时」就直接放行。
- 后果：被封的读者仍能签到、改资料、改密码；被删的管理员仍能封别人、过审作者。
- 改法：`UserStatusCache` 下沉到 `inkisle-common`，user 侧回源查本地 mapper 而非 Feign；
  `evictStatusCache` 里硬编码的 `RoleConstant.USER` 改成按实际 role 删。
- 成本：小

### P1-4 网关限流恰好漏掉最贵的接口

- 位置：`inkisle-gateway/.../filter/RateLimitFilter.java:35-39`
- 现状：`LIMITED_PREFIXES` 覆盖了书/章节/榜单/评论/签到，唯独没有 `/user/`、`/captcha/`、`/file/`。
- 后果：`/user/register` 每次跑 BCrypt（cost 10，60~100ms CPU），无限注册就是 CPU 放大器；
  `/captcha/image` 每次新建 `HttpSession` 并渲染 PNG；`/file/upload` 无限制。
- 改法：三个前缀加进去；登录/注册用独立更严的桶（0.1 QPS，burst 5），维度取 `username + IP`（在 user 服务用 Redis 实现，网关拿不到 body）；
  登录失败 `login:fail:{username}` 5 次锁 15 分钟。
- 成本：小

### P1-5 无条件信任 `X-Forwarded-For`，一个请求头换一个新桶

- 位置：`RateLimitFilter.java:86-94`、`RepeatSubmitAspect.java:86-92`（同一个错误两处）
- 现状：取 XFF 第一段作为客户端 IP。`curl -H "X-Forwarded-For: 1.1.1.$i"` 循环即可创建无限满桶。
- 改法：网关是边缘，直接 `remoteAddress`；确实在 LB 后面就配可信代理白名单，且取**最后**一段（代理追加的那个）。
  网关默认过滤器 `RemoveRequestHeader=X-Forwarded-For,X-Real-IP`，下游只信网关重写的值。
- 成本：小

### P1-6 审核状态「先查后写」，两个管理员同时审会互相覆盖

- 位置：`BookCommentServiceImpl.java:142-170` + `BookCommentMapper.xml:108-113`；章节侧同构；审计侧 `BookAuditRecordMapper.xml:30-44`
- 现状：`requirePending` 读到 `status=0`，随后 `UPDATE ... WHERE id=?` 不带状态条件。并发下「通过」「驳回」都能成功，后提交的覆盖前者，审核人/时间也互相覆盖。
  `resubmitAfterEdit` 的 `countPending` + `insert` 有同样竞态，表上也没有能兜底的唯一键。
- 改法：状态收进 WHERE：`UPDATE ... SET audit_status=#{s} WHERE id=#{id} AND audit_status=0`，用 `affected==0` 抛「已审过」，去掉前置 SELECT。
  审核记录加生成列唯一索引 `pending_book_id = IF(status=0, book_id, NULL)`。
- 成本：小

### P1-7 审核双写没有补偿，且写入顺序正好都选反了

- 位置：`BookAuditServiceImpl.java:52-62,92-95`，类注释 24-28 行
- 现状：注释声称「先写记录再改状态，改状态失败留下待审记录」——但方法有 `@Transactional`，失败时 insert 一起回滚，兜底不存在。
  `submit` 是先远程置待审再本地插记录：插入失败 → 书卡在待审、管理端无记录可审。
  `pass` 是三次写，第三步失败时本地回滚、远端已上架 → 书在线但作者看到「待审」、章节全不可见。
- 改法：「改书状态 + 放行章节」合并成一个 `/internal/book/{id}/approve`，一次远程调用单事务完成；
  本地记录状态最后写并加 `WHERE status=0`；失败落 `audit_outbox` 表 + `@Scheduled` 每 30s 扫，别上 MQ。
- 成本：中

### P1-8 全局异常全吞成 HTTP 200，Feign 调 void 接口时「失败当成功」

- 位置：`inkisle-common/.../GlobalExceptionHandler.java:17-20,38-42`、`BusinessException.java:9-16`
- 现状：所有分支 `return Result.fail(...)`，HTTP 恒 200，`BusinessException.code` 字段被丢弃。
  连带后果：`InternalBookController.updateChapterStatus` 抛「章节不存在」返回 `{code:500}` + HTTP 200，
  而 `BookClient.updateChapterStatus` 返回 `void`，Feign 不解码 body → 审核服务以为成功，日志还打了「章节审核通过」。
- 改法：改 `ResponseEntity<Result<Void>>`，状态码取 `e.getCode()`；补 `MethodArgumentTypeMismatch` / `HttpMessageNotReadable` / `MissingServletRequestParameter` / `DuplicateKey` 四个分支映射 400/409；
  `/internal/**` 单独一个 advice 直接抛 `ResponseStatusException`；Feign 加 `ErrorDecoder` 解析 `Result.code` 转本地异常。
- 成本：中

### P1-9 `@RepeatSubmit` 用的是互斥锁，2 秒防重窗口实际等于接口耗时

- 位置：`inkisle-book/.../aspect/RepeatSubmitAspect.java:56-68`
- 现状：`tryLock(0, expire, SECONDS)` … `finally { lock.unlock(); }`，业务一结束就放行。
  注释写的是「锁的秒数，过了这段时间才可以再调」，实际评论入库 50ms 后双击第二发直接进。
  释放逻辑本身是对的（`isHeldByCurrentThread`），是语义用错了。
- 改法：改用带 TTL 的去重令牌：`RBucket.trySet(now, expire, SECONDS)` 成功才执行，**不主动删**，让 TTL 自然过期；失败想立即重试就在 catch 里 `delete()`。
- 成本：小

### P1-10 签到位图的重建被「key 存在」短路，Redis 丢一次历史丢一年

- 位置：`inkisle-user/.../SignServiceImpl.java:141-147,159-163,173-181`
- 现状：`readBitmap` 只要 `raw.length > 0` 就返回；`rebuildFromDb` 只要 `readyKey` 在就返回空。
  时序：Redis 被清 → 用户先点签到 → Lua `SETBIT` 把 key 建成只有今天 1 bit → 之后永远走「有数据」分支，历史签到不可见直到跨年。
  另一条：位图被 LRU 淘汰而 `readyKey` 还在，重建也直接返回空。
- 改法：`sign()` 前先 `hasKey`，没有就先 `rebuildFromDb`（SETBIT 幂等，可叠加今天）；
  短路条件从「存在性」改成「完整性」（`raw.length >= offsetOf(today)/8 + 1`）；`readyKey` 只做负缓存，重建前同时校验主 key。
- 成本：中

### P1-11 改密只认 token，无验证码、无失败计数

- 位置：`inkisle-user/.../UserServiceImpl.java:171-173`
- 现状：`if (!PasswordUtil.matches(old, stored)) throw new BusinessException("原密码不对")`，不计次、不延时、无审计日志。
  登录至少挂了图形验证码，改密没有。
- 后果：token 泄露（XSS/共享设备/localStorage）→ 无限爆破原密码 → 改密 → 因为 P1-2 不吊销会话，受害者不掉线，很难察觉。
- 改法：二次验证 + `pwd:fail:{userId}` 5 次锁 15 分钟 + `log.warn` 审计（含 IP/UA，不含密码）；改密成功 `INCR auth:ver`。
- 成本：小

### P1-12 存量密码升级是无条件 UPDATE，会覆盖并发改密

- 位置：`UserServiceImpl.java:93-102`、`AuthorServiceImpl.java:84-93`、`AdminServiceImpl.java:59-68`
- 现状：`upgradeLegacyPassword` 直接 `updatePassword(id, encode(raw))`，不校验库里还是不是那条旧哈希。
  手机端旧密码登录触发升级 UPDATE 排队，PC 端同时改密成功，升级的 UPDATE 后落地把密码改回旧的 → 用户以为改成功，旧密码仍可登录。
- 改法：`WHERE id=? AND password=#{oldHash}`，影响 0 行就放弃升级。顺带把 MD5 比较换成 `MessageDigest.isEqual`。
- 成本：小

### P1-13 注册「先查后插」，并发撞唯一键返回 500「操作失败」

- 位置：`UserServiceImpl.java:50-60`、`AuthorServiceImpl.java:37-52`
- 改法：去掉前置 count，catch `DuplicateKeyException` 转 `BusinessException("用户名已存在")`。
- 成本：小

### P1-14 上传没有总大小上限，`join` 把整份文件拼进堆

- 位置：`inkisle-gateway/.../controller/FileController.java:58-62,94`、`application.yml:46-49`
- 现状：`max-in-memory-size: 5MB` 只是「超了就落临时文件」的阈值，不是拒绝；`DataBufferUtils.join` 仍会把整份读回 `byte[]`。
  前 4 字节伪造 PNG 头即可绕过 `isRealImage`，一个 1GB 的「图片」能打爆网关堆。
  失败分支 `Result.fail("文件保存失败：" + e.getMessage())` 把服务端绝对路径回了客户端。
- 改法：`max-disk-per-part: 5MB` + `max-parts: 2`；处理前看 `Content-Length` 超限 413；改流式边读边算摘要边写盘；
  按 `upload:daily:{uid}` 配额；错误只返回固定文案。
- 成本：中

---

## P2 · 性能与规模

### P2-1 四张表缺索引，作者数据页与删评论都是全表扫

- 位置：`sql/schema.sql:171,188-189,236`；SQL 在 `BookStatMapper.xml:17-18`、`BookCommentMapper.xml:37-47,120-123`
- 现状：`user_read_history` 只有 `uk_user_book(user_id, book_id)`，`WHERE book_id=?` 用不上；
  `book_comment` 只有 `idx_book_id`，`DELETE WHERE parent_id=?` 和 `/comment/mine` 的 `WHERE user_id=?` 全表扫。
  作者看一次数据页就是两次全表扫。
- 改法：补 `book_comment(parent_id)`、`book_comment(user_id)`、`user_read_history(book_id)`、`user_read_history(chapter_id)`、`user_bookshelf(book_id)`；
  两条统计子查询合成一次 `LEFT JOIN (SELECT book_id, COUNT(*) ...) GROUP BY book_id`。
- 成本：小

### P2-2 评论列表无分页，热书一次返回全量

- 位置：`BookCommentServiceImpl.java:47-67`、`BookCommentMapper.xml:11-27`
- 现状：`selectByBookId` 没有 `LIMIT`，`ORDER BY create_time ASC`，含正文 + LEFT JOIN 用户表，Java 里全量分组。
- 改法：一级评论分页 + 当前页回复批量 `IN`，两次查询拼树；索引换 `(book_id, parent_id, create_time)`。
- 成本：中

### P2-3 详情缓存把易变计数一起缓存，阅读数回写后不清

- 位置：`BookServiceImpl.java:110`、`VisitCounter.java:93`、`CacheConfig.java:31`
- 现状：`@Cacheable` 缓存整个 `BookInfo`（含 visitCount/commentCount/score），TTL 30 分钟；
  `flush()` 每分钟批量改库但从不 evict，也不 sync ES → 详情页阅读数和点击榜（直接读库）对不上，最长 30 分钟。
- 改法：缓存只放展示字段（`BookDetailVO`），计数单独短 TTL key；或退一步在 flush 成功后对成功的 bookId 做一次 pipeline `DEL`。
- 成本：中

### P2-4 字数、评论数是「每次写都全量重算」

- 位置：`BookChapterServiceImpl.java:261-268`、`BookChapterMapper.xml:89-93`、`BookInfoMapper.xml:220-227`
- 现状：`updateWordCount(bookId, selectTotalWordCount(bookId))` —— 3000 章的书每存一章扫 3000 行；
  评论每条增删审都 `COUNT(*)`。
- 改法：增量维护 `word_count = word_count - old + new`，`comment_count ± 1`；全量 SQL 保留给管理端对账。
- 成本：小

### P2-5 整本导入一个大事务包住文件 IO、解析和全部批次

- 位置：`BookChapterServiceImpl.java:177-233`
- 现状：`file.getBytes()`、正则切分、N 批 `insertBatch`、`refreshBookStat` 全在一个事务；`selectIdForUpdate` 从取号锁到提交。
  5000 章 = 几十秒大事务 + 长行锁，同书任何写操作排队。
- 改法：事务外解析 → `TransactionTemplate` 每 200 章一个短事务 → 最后单独刷一次统计；章节号预分配用 `INCRBY`。
- 成本：中

### P2-6 深翻页会打穿 ES 窗口并误触发熔断，把全站搜索降级成 LIKE 全表扫

- 位置：`BookSearchEngine.java:62-70,95`、`PageUtils.java:11`
- 现状：`pageNum` 允许到 10000、`pageSize` 到 100，`from+size > 10000` 抛 `IllegalArgumentException`，
  被 `catch (Exception)` 计入「ES 连续失败」，3 次后熔断 30 秒 → 全站回落到 `LIKE '%x%'`。
  一个用户翻到第 200 页即可触发。
- 改法：入口拦 10k 窗口；熔断只统计连接/超时/5xx，参数异常单列分支不计失败；真要深翻用 `search_after`。
- 成本：小

### P2-7 热度榜和阅读数能被单人刷上去

- 位置：`UserReadHistoryServiceImpl.java:26-32`、`RankServiceImpl.java:52-68`
- 现状：进度是幂等 upsert，但 `rankService.addHot(bookId)` 每调一次 +1，无去重无防重；`visitCounter.record` 同理。
  脚本循环几千次就能顶到榜首，前端翻页重试也会重复加分。
- 改法：`SET hot:uv:{weekKey}:{bookId}:{userId} 1 NX EX 604800`，返回 true 才加分；统计 UV 语义用 `PFADD/PFCOUNT` 更贴切。
- 成本：小

### P2-8 后台看板口径与业务表对不上，`days` 无边界

- 位置：`inkisle-stat/.../AdminStatMapper.xml:9-28`、`AdminStatController.java:35-38`
- 现状：`COUNT(*) FROM book_comment` 含待审/驳回，`COUNT(*) FROM book_info` 含未提交/下架，
  而 `book_info.comment_count` 只统计 `audit_status=1` → 看板 1200 / 作品页 800，运营当 bug 提。
  `?days=100000` 直接全表扫；`user_info.create_time`、`book_comment.create_time` 都没索引。
- 改法：同口径（`audit_status=2` / `=1` / `status=0`）；Service 里 `Math.min(Math.max(days,1),90)`；补 `idx_create_time`；时间条件写成两个显式边界，别用 `DATE()` 包列。
- 成本：小

### P2-9 审核列表无分页 + 缺 `(status, submit_time)` 组合索引

- 位置：`AdminAuditController.java:30-33`、`BookAuditRecordMapper.xml:10-17`
- 现状：`list(status)` 直接返回全部历史，`ORDER BY submit_time DESC` 只有单列 `idx_status`，要 filesort。表只增不删。
- 改法：加 `pageNum/pageSize`，索引换成 `idx_status_submit(status, submit_time)` 直接替代原索引。
- 成本：小

---

## P3 · 前端体验

### P3-1 异步响应没有序号守卫，翻章/换榜会被迟到的旧响应覆盖

- 位置：`ReadView.vue:89-108,157-164`、`RankView.vue:24-38`
- 现状：`await` 后直接写 `state.chapter`，不校验是不是最新一次点击。`LibraryView.vue:32-52` 已有正确写法（`seq` 守卫），这两处没沿用。
  附带：切章期间无 loading/禁用态，连点「下一章」时 `nextId` 没变，`router.push` 到同一 URL 被去重 → 看起来像「点了没反应」。
- 改法：`let loadSeq = 0` + `if (seq !== loadSeq) return`；配合 `AbortController` 取消上一次；翻章期间禁用两个按钮。
- 成本：小

### P3-2 榜单接口不返回 `categoryId`/`status`，分类标签全空、状态永远「连载中」

- 位置：`RankServiceImpl.java:182-195`（`toItem` 只放 9 个字段）、`RankView.vue:98,103`、`HomeView.vue:123`
- 改法：`toItem` 补 `categoryId`、`status` 两个字段（`BookInfo` 本来就有）。
- 成本：小

### P3-3 详情页不响应路由参数变化，前进/后退停在上一本书

- 位置：`BookView.vue:16,163-166`、`App.vue:13-17`
- 现状：只在 `onMounted` 里 `load()`，`/books/1 → /books/2` 组件被复用不重建。`ReadView` 反而 watch 了参数，两页不一致。
- 改法：`watch(() => route.params.id, ...)` 重置局部状态再加载。**不要**用 `<RouterView :key="$route.fullPath">` 一刀切，会把阅读页同路由翻章的复用也破坏。
- 成本：小

### P3-4 请求层无超时/取消，401 先跳转再抛错，并发时重复跳转

- 位置：`web/src/api/client.js:49-73,88-109`
- 现状：`fetch` 无 `signal` 无超时；401 分支直接 `location.href = ...` 然后继续 `throw`。
  `MeView.load()` 用 `Promise.all` 发 5 个请求，token 过期会连续 5 次重定向赋值，整页刷新丢进度和滚动位置。
  `ROLES` 里 author/admin 分支在这个 SPA 里没有对应路由，是死代码。
- 改法：`AbortSignal.any([signal, AbortSignal.timeout(10000)])`；401 用模块级 `redirecting` 去重，走 `router.replace` 而非整页重载；401 抛带 `code:401` 的标记错误，调用方静默；删掉多角色死代码。
- 成本：中

### P3-5 返回列表页必回顶部，滚动恢复发生在骨架屏高度上

- 位置：`App.vue:13-17`、`router/index.js:46-55`、`LibraryView.vue:16-21`
- 现状：`if (saved) return saved` 时 `state.books` 还是 `[]`，骨架页很矮，位置被裁掉；没有 keep-alive。
- 改法：列表页做数据+滚动缓存（模块级单例，命中则不请求，`nextTick` 后 `scrollTo`），或给三个列表页定向 `KeepAlive`（**不要**包 `ReadView`，它用 `onMounted` 拉章节）。
- 成本：中

### P3-6 目录一次性渲染全书章节

- 位置：`BookView.vue:281-290`、`ReadView.vue:300-313`
- 现状：无分页返回全书目录，几千个 `<li>` 全量渲染，每项还带内联 `--d` 动画延迟。
- 改法：最省事两行 CSS `content-visibility: auto; contain-intrinsic-size: auto 48px;`；进阶用 `RecycleScroller`；`--d` 改成 `i % 8`。
- 成本：小 / 大

### P3-7 首屏大封面也用 `loading="lazy"`，且无加载失败降级

- 位置：`components/BookCover.vue:35`
- 现状：无条件 `lazy`，首页 hero 和详情页大图都是首屏元素；`v-if="cover"` 一旦为真就永远走 `<img>`，404 直接破图，不会回退到自带的生成式封面。
- 改法：加 `priority` prop（`eager` + `fetchpriority=high`），`@error` 置 `failed` 回退文字封面。
- 成本：小

### P3-8 登录态没有统一 store，改完昵称头部不刷新

- 位置：`BookView.vue:17-18`、`SiteHeader.vue:9,36-43`、`MeView.vue:94-95`、`client.js:29-33`
- 现状：`computed(() => Boolean(auth.token()))` 内部无响应式依赖，会被永久缓存；`SiteHeader` 靠 `route.fullPath` 变化才刷新用户；
  `auth.save` 的 `localStorage.setItem` 没 try/catch，隐私模式下会在登录成功后被这行打断显示「登录失败」。
- 改法：`stores/session.js` 用 `reactive`，`main.js` 启动时 `hydrate()`；写入包 try/catch 只告警；`logout` 与 401 清理共用。
- 成本：中

### P3-9 签到盖章的 `setTimeout` 未清理

- 位置：`MeView.vue:141-146`
- 改法：定时器登记 + `onBeforeUnmount` 清理；或改成 CSS `animationend` 清 class，让浏览器管生命周期。
- 成本：小

---

## 顺手就能修的零碎（各 1 行）

| 问题 | 位置 | 改法 |
|---|---|---|
| `category.getParentId() == ROOT_ID` 拆箱，`parent_id` 为 NULL 时全站 500 | `BookCategoryServiceImpl.java:36` | `Long.valueOf(ROOT_ID).equals(...)` |
| `passAllChapters` 只清目录缓存，没清详情缓存，口径与 `updateChapterStatus` 不一致 | `InternalBookController.java:99-104` | 补 `evictBook` |
| 删书级联删了章节/正文，但 `CHAPTER_CATALOG`/`CHAPTER_CONTENT` 留在 Redis 到 TTL | `BookServiceImpl.java:266-281` | 删完批量 evict |
| `AdminUserController` 用 `params.get("userId").toString()`，缺字段 NPE 走 500 | `AdminUserController.java:36-39` | 改 DTO + `@Valid` |
| 所有 `create_time/update_time` 是 `DEFAULT NULL`，漏赋值就静默丢出按天统计 | `sql/schema.sql:19,41,102,186` | `NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE` |
| 注册成功把 username 放 query，登录页从不回填 | `RegisterView.vue:34` / `LoginView.vue:12` | 读 `route.query.username` |
| 网关路由缺 `/admin/info`，`AdminController.info` 走网关必 404 | `gateway/application.yml:15-41` | 补路由 |
| `pom.xml` 引了 `spring-ai-bom` 但零使用；`inkisle-audit` 引了 Redis 但零代码 | 根 pom / `inkisle-audit/pom.xml:45-48` | 删依赖 |
| 全仓库无 traceId/MDC，一次送审穿 4 个服务只能靠时间戳对日志 | — | `OncePerRequestFilter` 生成 `X-Trace-Id` + Feign 透传 |
| 验证码答案存 `HttpSession`，user 服务多实例时随机校验失败 | `CaptchaUtil.java:76` | Spring Session + Redis（2 分钟 TTL、一次性消费已有） |
| 作者编辑送审后 `resubmitAfterEdit(bookId, authorId)` 完全没用 `authorId` | `BookAuditServiceImpl.java:101-105` | 补归属校验或去掉该参数 |

---

## 建议动手顺序

**第一批（半天，全是真 bug，改完就不会再挂）**
P0-1 锁等待 → P0-3 路由 base → P0-5 星级 → P0-4 我的评论 → P0-6 同名章节 → P0-2 内部鉴权

**第二批（一天，补齐上文各项能力宣称背后的缺口）**
P1-1 缓存 evict 移到 afterCommit → P1-6 审核 CAS → P1-9 防重语义 → P1-10 签到重建 → P1-12 密码升级 CAS

**第三批（批量补齐，性价比最高）**
P2-1 索引 → P2-4 增量统计 → P2-6 ES 窗口 → P1-4/P1-5 限流两处

**暂不建议做**
`jti` 黑名单（用版本号更省）、MQ 替代双写（本地消息表够了）、虚拟列表（`content-visibility` 先顶）、分布式事务框架。
理由都一样：这个项目的数据规模和部署形态撑不起，做了反而像背模板。

---

## 修复前后的量化对比

1. 服务拆分后「本地事务持锁 + 同步 Feign 回写同一行」使编辑接口卡在 50 秒锁等待超时；
   改为提交后触发、状态幂等写，该接口不再出现超时。
2. 书籍与章节缓存的失效从事务内迁到 `afterCommit` 回调，消除「删缓存 → 未提交 → 并发回填旧值」
   的窗口，正文脏读上限从 2 小时降到 0，同类问题收敛为一个公共组件。
3. 审核并发用条件更新加生成列唯一索引，从「先查后写」改为数据库级幂等，重复审核与重复待审记录归零；
   补齐 5 处缺失索引后，作者数据页由两次全表扫降为索引覆盖。
