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

## 目录结构

```
inkisle
├── sql
│   ├── schema.sql                  建库建表脚本（含全文索引与初始数据）
│   ├── upgrade-audit-status.sql    存量库升级：为 book_info 补上架状态
│   ├── upgrade-book-stats.sql      存量库升级：阅读数、字数、评论数、评分
│   ├── upgrade-chapter-unique.sql  存量库升级：章节号改唯一索引
│   ├── upgrade-content-audit.sql   存量库升级：章节与评论的审核状态
│   └── upgrade-home-book.sql       存量库升级：首页推荐位
├── web/                            前端页面，由后端静态资源映射托管
│   ├── *.html                      读者端 / 作者端 / 管理端共 24 个页面
│   ├── css/style.css               全站样式
│   └── js/api.js                   请求封装、登录态、导航渲染
├── src/main/java/com/inkisle
│   ├── annotation/                 @RepeatSubmit 等自定义注解
│   ├── aspect/                     防重复提交切面
│   ├── common/                     Result、BusinessException、全局异常处理
│   ├── config/                     WebMvc、缓存、Redisson 等配置
│   ├── controller/                 三端接口
│   ├── dto/                        入参对象
│   ├── entity/                     与表一一对应的实体
│   ├── interceptor/                登录拦截器
│   ├── mapper/                     Mapper 接口
│   ├── service / service.impl      业务实现
│   └── util/                       JWT、MD5 等工具
└── src/main/resources
    ├── application.yml
    └── mapper/*.xml                
```

测试在 `src/test` 下：`support` 放共用基类与测试库初始化，`service` 是服务层用例，`util` 是纯函数用例。

## 本地运行

依赖 MySQL 和 Redis：

```bash
# 1. 导入表结构与初始数据
mysql -uroot -p < sql/schema.sql

# 2. 启动（默认端口 8080）
mvn spring-boot:run
```

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
- 审核记录表 `book_audit_record` 只留历史，与作品状态在同一事务内写入；管理员下架不属于送审，不写入该表。

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
