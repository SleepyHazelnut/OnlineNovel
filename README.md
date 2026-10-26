# inkisle 墨屿小说

PC 端在线阅读网站。后端为 Spring Boot 3.3 微服务，读者端为 Vue 3 单页应用，作者端与管理端为网关托管的原生页面。

## 技术栈

- Java 21、Spring Boot 3.3
- Spring Cloud Alibaba 2023.0.3.2：Nacos 注册发现、Gateway、OpenFeign
- MySQL 8、MyBatis
- Redis（缓存、签到位图、榜单 ZSet）、Redisson 分布式锁
- Elasticsearch 8.13（可选，默认使用 MySQL 全文索引）
- JWT、Spring AI 1.0.0 + DeepSeek
- Vue 3 + Vite，未引入 UI 框架

## 模块与端口

| 模块 | 端口 | 职责 |
| --- | --- | --- |
| inkisle-gateway | 8080 | 对外入口，托管读者端、作者端与管理端页面及上传 |
| inkisle-user | 8081 | 账号、登录、签到、积分 |
| inkisle-book | 8082 | 作品、章节、分类、评论、评分、榜单、书架、创作助手、搜索 |
| inkisle-audit | 8083 | 作品与章节审核 |
| inkisle-stat | 8084 | 后台数据看板 |

服务已拆分，数据库未拆分：跨服务修改数据经 Feign 调用，读取仍直接联表。

## 运行

依赖 MySQL、Redis、Nacos；仅在 `SEARCH_ENGINE=es` 时需要 Elasticsearch。

```bash
# 启动 Nacos、Redis 与 Elasticsearch（MySQL 使用本机实例）
docker compose up -d

# 建库并写入初始分类与演示账号
mysql -uroot -p < sql/schema.sql

# 构建读者端，产物输出到网关 static/app
cd web && npm install && npm run build:gateway && cd ..

# 启动服务，网关最后
mvn -pl inkisle-gateway -am spring-boot:run
```

访问 `http://localhost:8080/app/`。`schema.sql` 会删表重建，已有数据的库改用 `sql/` 下的升级脚本。

## 配置

| 变量 | 默认值 |
| --- | --- |
| `MYSQL_PASSWORD` | `123456` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `NACOS_ADDR` | `localhost:8848` |
| `JWT_SECRET` | 开发占位串 |
| `FILE_UPLOAD_PATH` | `../upload` |
| `AI_API_KEY` / `AI_MODEL` | 空 / `deepseek-chat` |
| `SEARCH_ENGINE` / `ES_URIS` | `mysql` / `http://localhost:9200` |
| `RATE_LIMIT_QPS` / `RATE_LIMIT_BURST` | `20` / `40` |

## 演示账号

| 端 | 入口 | 账号 |
| --- | --- | --- |
| 读者 | `/app/login` | `reader1 / 123456` |
| 作者 | `/author-login.html` | `bimo / 123456` |
| 管理 | `/admin-login.html` | `admin / admin123` |

## 功能

- 读者端：书库、分类、搜索、详情、阅读、书架、阅读记录、评论评分、签到积分、排行榜
- 作者端：作品与章节管理、txt 整本导入、上下架、数据查看、AI 改写
- 管理端：作品与章节审核、评论审核、作者资质、分类、首页推荐、用户管理、数据看板

作品状态分未提交、待审核、已通过、已驳回、已下架五种，仅「已通过」对读者可见；章节与评论为待审、通过、驳回三态。

## 测试

```bash
mvn test
```

137 个用例，覆盖服务层、接口层与工具类。用例连接独立的 `inkisle_test` 库，首次运行自动建库；跨服务调用在测试中替换为 mock，断言仍查表验证最终状态。
