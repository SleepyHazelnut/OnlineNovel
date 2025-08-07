# 墨屿小说 inkisle

一个 PC 端的在线阅读网站。

 Spring Boot + HTML + CSS + JS，分读者、作者、管理三端，由后端托管。

## 技术栈

| 分类 | 选型 | 说明 |
| --- | --- | --- |
| 语言 / 运行时 | Java 21 | |
| 框架 | Spring Boot 3.3.0 |  |
| 持久层 | MyBatis 3.0.3 | |
| 数据库 | MySQL 8 |  |
| 缓存 | Redis（Spring Cache） |  |
| 分布式锁 | Redisson 3.32.0 |  |
| 认证 | JWT（jjwt 0.12.5） |  |
| AI | Spring AI 1.0.0 + DeepSeek |  |
| 参数校验 | spring-boot-starter-validation |  |
| 服务治理 | Spring Cloud 2023.0.3 + Alibaba 2023.0.3.2 | Nacos 注册发现、Gateway 网关、OpenFeign 调用 |

## 目录结构

```
inkisle
├── sql                             建库脚本与存量库升级脚本
├── inkisle-common                  实体、入参、返回体、JWT，以及服务间调用的 Feign 接口
├── inkisle-gateway                 8080，对外唯一入口，托管静态页并按前缀转发
├── inkisle-user                    8081，账号、登录、签到、后台的用户与作者管理
├── inkisle-book                    8082，作品、章节、分类、评论、评分、榜单、书架
├── inkisle-audit                   8083，作品与章节审核
└── inkisle-app                     9000，原单体；创作助手与文件上传还没拆，其余已迁走
```

服务已拆分，数据库未拆分：三张表以上的联表查询较多，拆库需改写的 SQL 量很大，先按服务划分写入边界，
跨服务的写操作走 Feign，读仍可直接联表。

测试与代码同仓，各服务自己的 `src/test` 下：`support` 放共用基类与测试库初始化，`service` 是服务层用例，`controller` 是接口用例，`util` 是纯函数用例。
原单体的测试已随代码拆到对应服务，剩下的创作助手与统计没有用例。

## 本地运行

依赖 MySQL、Redis 和 Nacos：

```bash
# 1. 导入表结构与初始数据
mysql -uroot -p < sql/schema.sql

# 2. 启动 Nacos（2.x 单机）
sh $NACOS_HOME/bin/startup.sh -m standalone

# 3. 启动服务，网关最后
mvn -pl inkisle-gateway -am spring-boot:run
```

各服务的端口与对外路径：

| 服务 | 端口 | 网关转发的前缀 |
| --- | --- | --- |
| inkisle-gateway | 8080 | 静态页与 `/upload/**` 由自身处理，其余按前缀转发 |
| inkisle-user | 8081 | `/user`、`/sign`、`/captcha`、`/author`、`/admin/login`、`/admin/author`、`/admin/user` |
| inkisle-book | 8082 | `/book`、`/chapter`、`/category`、`/comment`、`/rating`、`/rank`、`/home`、`/bookshelf`、`/history`、`/author/book`、`/author/chapter`、`/admin/book`、`/admin/category`、`/admin/comment`、`/admin/home` |
| inkisle-audit | 8083 | `/admin/audit`、`/admin/chapter` |
| inkisle-app | 9000 | `/author/ai`、`/file`、`/admin/stat` |

配置项都预留环境变量，需要覆盖时：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MYSQL_PASSWORD` | `123456` | 数据库密码 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 地址 |
| `JWT_SECRET` | 开发用占位串 |  |
| `FILE_UPLOAD_PATH` | `./upload` | 上传目录 |
| `AI_API_KEY` | 占位串 |  |
| `AI_BASE_URL` | `https://api.deepseek.com` |  |
| `AI_MODEL` | `deepseek-chat` | |
| `NACOS_ADDR` | `localhost:8848` | 注册中心地址 |
| `LEGACY_URI` | `http://localhost:9000` | 网关转发给原单体的地址 |

启动后打开 `http://localhost:8080/` 就是书库首页。三端入口和演示账号：

| 端 | 入口页 | 主要页面 | 演示账号 |
| --- | --- | --- | --- |
| 读者端 | `/login.html` | 书库、书籍详情、阅读、我的书架、签到、排行榜、个人中心（含我的评论） | `reader1 / 123456` |
| 作者端 | `/author-login.html` | 我的作品、作品编辑、章节管理、AI 创作助手 | `bimo / 123456` |
| 管理端 | `/admin-login.html` | 数据看板、首页推荐、书籍审核、章节审核、作者审核、评论管理、分类管理、用户管理 | `admin / admin123` |

上表的账号与书籍分类由 `sql/schema.sql` 末尾的初始化语句写入，空库导入后即可登录。
书籍与章节内容需用作者账号创建，或在章节管理页导入 txt。

已建库的环境不要重复导入 `schema.sql`（会删除表）：改用升级脚本补充字段，脚本会按原有口径回填可见性，迁移前后读者看到的内容不变。

```bash
mysql -uroot -p inkisle < sql/upgrade-audit-status.sql
```

## 作品上架流程

作品是否对读者可见只看 `book_info.audit_status` 一列，只有「已通过」会出现在书库、搜索、榜单和详情页。

| 状态 | 含义 | 读者可见 |
| --- | --- | --- |
| 0 未提交 | 刚建好，还没送审 | 否 |
| 1 待审核 | 已送审，等管理员处理 | 否 |
| 2 已通过 | 审核通过，正常上架 | 是 |
| 3 已驳回 | 需要作者修改后重新提交 | 否 |
| 4 已下架 | 上架后被管理员下架 | 否 |

几条约定：

- 新书从「未提交」起步，作者点「提交审核」后进入待审，管理员通过才上架。
- 已上架的作品修改书名、作者名、简介、封面或分类，会自动重新送审并暂时从读者端消失；修改章节正文只让该章回到待审，作品本身仍在架上。
- 管理员可以在「已上架」里下架作品，「已下架」里恢复上架。下架必须写原因，原因会显示在作者的作品列表里。
- 审核记录表 `book_audit_record` 只留历史；管理员下架不属于送审，不写入该表。
- 记录归审核服务、作品状态归书籍服务，两者不在同一个事务里：先写入记录再修改状态，状态修改失败会留下一条待审记录，重新审核一次即可。

## 章节与评论审核

作品过审代表整本书可以上架，但正文与评论才是违规内容最常出现的地方，两者另有一套三态（0 待审 / 1 通过 / 2 驳回）：

- 新增或修改的章节进入待审，读者目录与「上一章 / 下一章」仅展示已通过的章节；作者端能看到自己全部章节与驳回原因。
- 作品首次过审时，该书所有待审章节一并置为通过 —— 此时审核对象是整本书，逐章重复审核没有意义。
- 读者发言先进待审，本人能在书籍详情页看到自己待审的评论，其他人看不到；编辑已通过的评论会回到待审，避免先发布合规内容再改为违规内容。
- `book_info.comment_count` 只统计已通过的评论，状态每变一次都重算。
- 存量库升级执行 `sql/upgrade-content-audit.sql`，脚本把已有章节与评论置为通过，避免升级后内容从读者端集体消失。

## 测试

```bash
mvn test
```

用例连接 `inkisle_test` 库，与开发库分开，首次运行会自动建库并导入 `sql/schema.sql`。
跨服务的调用在测试里换成 mock，落库效果保留：审核服务改作品状态走 Feign，
测试中仅拦截 HTTP 层，断言仍然查表看最终状态，而不是「有没有调一次接口」。
