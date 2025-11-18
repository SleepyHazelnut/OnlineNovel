# 墨屿小说 inkisle

PC 端在线阅读网站。后端为 Spring Boot 3.3 多服务架构。读者端前端是 Vue 3 单页应用，
作者端与管理端仍是网关托管的原生页面。三端的账号体系与页面入口相互独立。

## 技术栈

- Java 21 / Spring Boot 3.3.0
- Spring Cloud 2023.0.3 + Alibaba 2023.0.3.2：Nacos 注册发现、Gateway 网关、OpenFeign
- MySQL 8 + MyBatis 3.0.3
- Redis：缓存、BitMap 签到、ZSet 榜单
- Redisson 分布式锁
- Elasticsearch 8.13（可选，默认使用 MySQL 全文索引）
- JWT（jjwt 0.12.5）
- Spring AI 1.0.0 + DeepSeek，作者端 AI 改写
- Vue 3.5 + Vite 8 + vue-router：读者端单页应用，未引 UI 框架

## 模块

```
inkisle
├── sql           建库脚本与存量库升级脚本
├── inkisle-common   实体、DTO、JWT、Feign 接口
├── inkisle-gateway  网关、读者端托管、上传
├── inkisle-user     账号、登录、签到
├── inkisle-book     作品、章节、分类、评论、评分、榜单、书架、创作助手
├── inkisle-audit    作品与章节审核
├── inkisle-stat     后台数据看板
└── web              读者端 Vue 应用
```

服务已拆分，数据库未拆分。书籍服务中存在多处三表以上联表，拆库需改写为多次 RPC 后在内存中拼装，
改动量大而收益有限。写入按服务划分边界，跨服务修改数据经 Feign 调用，读取仍直接联表。

上传与创作助手不单独建服务：上传的读取路径本就在网关，读写同一目录置于同一进程可省去一次转发；
创作助手仅作者使用，而作者端接口本就归属书籍服务。

## 前端

读者端在 `web/`，Vue 3 + Vite 单页应用，没有引 UI 框架和状态库。颜色、间距、缓动集中在
`src/styles/tokens.css`，页面按发现、阅读、账号三条链路组织，接口请求收在 `src/api/`。

构建产物落到 `inkisle-gateway/src/main/resources/static/app`，由网关在 `/app/**` 下托管。
页面挂在 `/app` 前缀下，避免与 `/book`、`/rank` 这些接口前缀同名；网关对该路径做了回退，
刷新 `/app/books/1` 这类前端路由地址仍返回 `index.html`，交由 vue-router 接管。

作者端与管理端仍是 `static/` 下的原生页面，暂未迁移。

## 端口与路由

| 模块 | 端口 | 网关转发前缀 |
| --- | --- | --- |
| gateway | 8080 | 读者端 `/app/**` 与 `/upload/**` 由自身处理，作者端、管理端页面亦在此 |
| user | 8081 | `/user`、`/sign`、`/captcha`、`/author`、`/admin/login`、`/admin/author`、`/admin/user` |
| book | 8082 | `/book`、`/chapter`、`/category`、`/comment`、`/rating`、`/rank`、`/home`、`/bookshelf`、`/history`、`/author/book`、`/author/chapter`、`/author/ai`、`/admin/book`、`/admin/category`、`/admin/comment`、`/admin/home` |
| audit | 8083 | `/admin/audit`、`/admin/chapter` |
| stat | 8084 | `/admin/stat` |

网关是对外唯一入口，其余请求按前缀转发。`/author/**` 下同时存在归属书籍服务（作品、章节、AI 改写）
与归属用户服务（登录、注册）的接口，因此拆成两条路由并指定匹配顺序，避免转发到错误的服务。

## 鉴权

登录成功后签发 JWT，claims 中包含用户 id 与角色，有效期 24 小时。三端 token 分开存放，
读者端登录不会使作者端登录态失效。

各服务注册 `LoginInterceptor` 拦截 `/**`，从 `Authorization: Bearer` 中取出 token 校验，
再按路径判断角色：`/admin/**` 要求管理员，`/author/**` 要求作者，其余仅校验登录。
游客可访问的接口在对应服务中显式放行，如书籍、章节、榜单、分类与评论列表；
注册登录、图形验证码以及服务间调用的 `/internal/**` 同样放行。

封禁状态在每个受保护请求上校验：token 签发后账号可能被封，只拦登录要等一天才生效。
状态缓存在 Redis 里（30 秒），封禁时用户服务删掉对应 key，实际即时生效；
正常请求省掉一次跨服务查库。

密码存 BCrypt（自带盐）。早先是无盐 MD5，登录校验时按哈希前缀识别格式，
旧格式验证通过后就地重算写回，存量账号无需改密。

## 接口约定

所有接口返回 `Result`，结构为 `{ code, msg, data }`，`code` 取值与 HTTP 语义一致：
200 成功、400 参数错误、401 未登录、403 无权限、404 资源不存在、500 业务失败。
业务异常由 `GlobalExceptionHandler` 统一转换，Controller 不自行拼接响应。

## 运行

依赖 MySQL、Redis 与 Nacos。搜索默认使用 MySQL 全文索引，仅在设置 `SEARCH_ENGINE=es` 时需要 Elasticsearch。

```bash
# 启动 Nacos、Redis 与 Elasticsearch，MySQL 使用本机实例
docker compose up -d

# 建库，并写入初始分类与演示账号
mysql -uroot -p < sql/schema.sql

# 构建读者端，产物写进网关的 static/app
cd web && npm install && npm run build:gateway && cd ..

# 启动服务，网关最后
mvn -pl inkisle-gateway -am spring-boot:run
```

打开 `http://localhost:8080/app/`。

只改前端时不必每次构建，Vite 开发服务器按接口前缀代理到网关：

```bash
cd web && npm run dev
```

打开 `http://localhost:5173/`。

`schema.sql` 会删除并重建表。已建库的环境不要重复导入，改用 `sql/` 下的升级脚本补充字段与数据：
`upgrade-audit-status.sql`、`upgrade-content-audit.sql`、`upgrade-book-stats.sql`、
`upgrade-chapter-unique.sql`、`upgrade-home-book.sql`。

## 配置

配置项均可通过环境变量覆盖。

| 变量 | 默认值 |
| --- | --- |
| `MYSQL_PASSWORD` | `123456` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `NACOS_ADDR` | `localhost:8848` |
| `JWT_SECRET` | 开发用占位串，网关与各服务共用 |
| `FILE_UPLOAD_PATH` | `../upload` |
| `AI_API_KEY` / `AI_BASE_URL` / `AI_MODEL` | 空 / `https://api.deepseek.com` / `deepseek-chat` |
| `SEARCH_ENGINE` | `mysql`，设为 `es` 时使用索引 |
| `ES_URIS` | `http://localhost:9200` |
| `RATE_LIMIT_QPS` / `RATE_LIMIT_BURST` | `20` / `40`，网关按来源 IP 限流 |

## 演示账号

| 端 | 入口 | 账号 |
| --- | --- | --- |
| 读者 | `/app/login` | `reader1 / 123456` |
| 作者 | `/author-login.html` | `bimo / 123456` |
| 管理 | `/admin-login.html` | `admin / admin123` |

## 功能

读者端：书库、分类、搜索、书籍详情、阅读、书架、阅读记录、评论评分、签到、积分、排行榜、个人中心。

作者端：作品增删改、章节管理、整本 txt 导入、作品上下架、数据查看、AI 创作助手。

管理端：作品审核、章节审核、评论审核、作者资质审核、分类维护、首页推荐位、用户管理、数据看板。

作品状态共五种：未提交、待审核、已通过、已驳回、已下架，仅「已通过」对读者可见。
书名、简介、封面等展示信息修改后重新送审；章节正文修改只使该章回到待审状态。

章节与评论另有三态（待审 / 通过 / 驳回）。作品首次过审时，该书所有待审章节一并放行。

排行榜分人气、点击、新书、更新四张。人气榜是 Redis ZSet，按 ISO 周分桶，
查询时合并本周与上周：全年一个 key 只会一路累加，排的是历史总量，
「本周强推」名不副实；只取本周又会在周一凌晨清空。旧桶 15 天后自动过期。

整本导入按 200 章一批写库。逐章插入时五百章要一千次往返，
导入大文件的时间几乎全花在路上。

## 积分

积分只做两件事：记一本账，留一个出口。没有商城、任务面板和徽章墙——
维护这些的复杂度属于电商，一个出口就够证明「行为到结果」这条线是通的。

| 行为 | 分值 | 上限 |
|---|---|---|
| 每日签到 | +2 | 每天 1 次 |
| 连签 3 / 7 / 30 天 | +3 / +8 / +20 | 每个里程碑一次 |
| 读完一章 | +1 | 每天 20 章 |
| 评分 | +3 | 每本第一次 |
| 评论过审 | +5 | 每天 3 条 |
| 荐书票 | −20 | 每本一次 |

等级看累计获得，不看余额（看余额会让用户不敢花）。票数进首页「读者荐书」板块，
是唯一有外部性的出口。规则写死在 `PointBizType` 里，不引配置中心。

账本在用户服务，三张表：`user_point_ledger`（流水，只增）、`user_point_account`（余额，流水的物化结果）、
`user_recommend_vote`（票）。幂等靠 `(user_id, biz_type, biz_id)` 唯一键，写库用 `INSERT IGNORE`
的 0 行判定，不做「先查再插」。扣减用条件更新 `WHERE balance >= ?`，扣不出负数。

跨服务加分统一走 `PointReporter`：事务提交后才发请求，失败只记日志不抛——
积分是旁路，用户服务抖一下不该让评分、评论跟着失败。余额与流水求和的偏差由
`GET /admin/point/reconcile` 比对，不做自动修正。设计与取舍见 `docs/POINT-SYSTEM.md`。

## 搜索

默认使用 MySQL 全文索引：两字以上使用 `MATCH ... AGAINST`，单字退化为 LIKE，不依赖外部组件。

设置 `SEARCH_ENGINE=es` 后使用 Elasticsearch，字段权重为书名 3、作者名 2、简介 1，同分按阅读数排序。
索引只收录已上架的作品，写库后同步，事务内的调用推迟到提交之后。Elasticsearch 不可用时自动退回 MySQL，
`POST /admin/book/reindex` 可全量重建索引。

## 缓存

三个区域分开设 TTL，按各自的变化频率走：

| 区域 | 内容 | TTL | 何时清 |
|---|---|---|---|
| `book` | 作品详情 | 30 分钟 | 改状态、删作品、章节变动 |
| `chapterCatalog` | 读者端目录，只含已过审章节 | 30 分钟 | 增删章节、审核状态变化 |
| `chapterContent` | 章节正文 | 2 小时 | 作者改稿、删章 |

读取统一走 `CacheAside`，三种常见失效场景各有对策：

- **穿透**：查不到也写缓存，目录存空列表、正文存空串。写入路径会删 key，所以不会出现「刚传上去读不到」
- **击穿**：未命中时用 Redisson 锁只放一个线程回源，其余线程等三轮（约 90ms）读它的结果，等不到就自己回源，不让请求卡住
- **雪崩**：`JitterRedisCacheWriter` 给每个 key 的过期时间加 0~15% 随机量，同一批数据不会在同一秒集体失效

key 落到作品 id 与章节 id 上，写入时只删对应的那一个，不做整区清空。
正文不可变，改一个字就把 2 小时的缓存作废，代价能接受。

## 阅读链路

一次翻章（读正文 + 上报进度）原先要碰 8 次数据库，其中 2 次是写。现在 3 次，且读接口不再写库。

| | 原来 | 现在 |
| --- | --- | --- |
| 读章节 | `selectById` | `selectById` |
| 可见性 | 直接查书，绕过详情缓存 | 复用详情缓存 |
| 正文 | `selectByChapterId` | 缓存命中，改稿时删 key |
| 上下章 | 两次单查 | 一条语句两个标量子查询 |
| 阅读计数 | 接口内 `UPDATE + 1` | Redis 累加，定时批量回写 |
| 进度 | 先查后写 | 一次 `ON DUPLICATE KEY UPDATE` |

计数回写由定时任务每分钟做一次，攒成一条 `UPDATE` 带多本书的增量。
计数是统计不是账，写失败就退回直接写库，Redis 丢了最多丢一小段增量。

## 保护与降级

- **限流**：网关按来源 IP 做令牌桶（默认 20/s，突发 40），只拦接口路径，静态资源不放限。
  桶在进程内，多实例部署时各实例独立计数
- **搜索降级**：ES 失败回落 MySQL 全文索引；连续 3 次失败后熔断 30 秒，
  窗口内直接走 MySQL，不让每个请求都去等一遍超时
- **缓存降级**：Redis 读失败按未命中处理，回落查库，不拦正常请求
- **级联调用**：删作品时清理审核记录放在事务提交之后，远程慢不再拖长本地事务；
  提交后已无法回滚，失败记日志由管理端补偿
- **防重复提交**：锁按「主体 + 接口 + 业务主键」拼 key，同一用户连评两本书互不影响；
  未登录时主体退到 IP + UA，不再共用一个 null
- **签到**：位图在 Redis，每次签到的结果同时落 `user_sign`；
  发现位图不在就按年从库里重建，缓存清空不会把签到记录一起抹掉

## 测试

```bash
mvn test
```

用例连接独立的 `inkisle_test` 库，与开发库分开，首次运行自动建库并导入 `sql/schema.sql`。
跨服务调用在测试中替换为 mock，落库行为保留：例如审核服务经 Feign 修改作品状态，
测试只拦截 HTTP 层，断言查表验证最终状态，而不是校验调用次数。

共 137 个用例，覆盖服务层、接口层与工具类。创作助手没有用例，调用需要真实的 API key。
测试环境不接 Redis：阅读计数切到直接写库、账号状态直接走 mock 的 Feign；
依赖 Redis 的逻辑（榜单合并、签到回填）由代码评审与手工验证覆盖。
