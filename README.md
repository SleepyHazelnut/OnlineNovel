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
├── sql/schema.sql                  建库建表脚本（含全文索引）
├── web/                            前端页面，由后端静态资源映射托管
│   ├── *.html                      读者端 / 作者端 / 管理端共 20 个页面
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
| 读者端 | `/login.html` | 书库、书籍详情、阅读、我的书架、签到、排行榜、个人中心 | `reader1 / 123456` |
| 作者端 | `/author-login.html` | 我的作品、作品编辑、章节管理、AI 创作助手 | `bimo / 123456` |
| 管理端 | `/admin-login.html` | 数据看板、书籍审核、作者审核、用户管理 | `admin / admin123` |

上表的账号与书籍分类由 `sql/schema.sql` 末尾的初始化语句写入，空库导入后即可登录。
书籍与章节内容需用作者账号创建，或在章节管理页导入 txt。

## 测试

```bash
mvn test
```
