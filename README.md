

## 快速开始

### 1. 环境要求

- **JDK 21**（`java -version` 确认）
- **Maven 3.8+**
- **MySQL 8.0+**
- **IDE**：IntelliJ IDEA 推荐

### 2. 创建数据库

打开 MySQL 命令行或图形化工具，执行：

```sql
CREATE DATABASE portfoliodb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

> 表结构（`schema.sql`）和初始化数据（`data.sql`）由 Spring Boot 启动时自动执行，无需手动建表。

### 3. 修改数据库连接配置

打开文件：

```
src/main/resources/application.properties
```

修改以下三行为你自己的 MySQL 账号信息：

```properties
# 改成你的 MySQL 地址和数据库名
spring.datasource.url=jdbc:mysql://localhost:3306/portfoliodb?useSSL=false&allowPublicKeyRetrieval=true

# 改成你的 MySQL 用户名
spring.datasource.username=root

# 改成你的 MySQL 密码
spring.datasource.password=root
```

> 如果你的 MySQL 不在本机，把 `localhost:3306` 改成对应的 IP 和端口。

### 4. 运行项目

#### 方式一：IDEA 运行

1. 用 IDEA 打开 `portfolio-manager` 文件夹

2. 等待 Maven 自动下载依赖（右下角进度条）

3. 找到 `src/main/java/com/portfolio/PortfolioApplication.java`

4. 右键 → **Run 'PortfolioApplication'**

5. 控制台出现以下日志即成功：
   
   ```
   Tomcat started on port 8080 (http)
   ```

#### 方式二：命令行运行

```bash
# 进入项目根目录
cd portfolio-manager

# 编译（首次运行会自动下载依赖）
mvn clean compile

# 运行
mvn spring-boot:run
```

### 5. 访问页面

浏览器打开：

```
http://localhost:8080
```

首次访问会看到组合总览页面（Portfolio Overview），通过顶部 Tab 可切换到投资操作页面（Investment Operations）。

---

## 项目结构

```
portfolio-manager/
├── pom.xml                          ← Maven 配置
├── API接口文档.md                    ← 接口文档
├── README.md                        ← 本文件
└── src/
    └── main/
        ├── java/com/portfolio/
        │   ├── PortfolioApplication.java      ← 启动类
        │   ├── controller/                    ← 控制层（接收请求）
        │   │   ├── PortfolioController.java   ← 组合总览、持仓列表
        │   │   ├── TransactionController.java ← 买入/卖出
        │   │   ├── StockController.java       ← 标的查询、价格历史
        │   │   └── GlobalExceptionHandler.java← 全局异常处理
        │   ├── dto/                           ← 数据传输对象（请求/响应格式）
        │   │   ├── PortfolioOverviewResponse.java
        │   │   ├── HoldingResponse.java
        │   │   ├── WeeklyPerformanceResponse.java
        │   │   ├── StockInfoResponse.java
        │   │   ├── PriceHistoryResponse.java
        │   │   ├── TransactionRequest.java
        │   │   └── TransactionResponse.java
        │   ├── model/                         ← 数据模型（对应数据库表）
        │   │   ├── Stock.java
        │   │   ├── Portfolio.java
        │   │   ├── Holding.java
        │   │   ├── Transaction.java
        │   │   └── PortfolioSnapshot.java
        │   ├── repository/                    ← 数据访问层（接口 + JDBC实现）
        │   │   ├── *Repository.java           ← 接口定义
        │   │   └── Jdbc*Repository.java       ← JDBC 实现
        │   └── service/                       ← 业务逻辑层（接口 + 实现）
        │       ├── PortfolioService.java / PortfolioServiceImpl.java
        │       ├── TransactionService.java / TransactionServiceImpl.java
        │       ├── StockService.java / StockServiceImpl.java
        │       └── PriceService.java / SimulatedPriceService.java
        └── resources/
            ├── application.properties         ← 配置文件（MySQL连接等）
            ├── schema.sql                     ← 建表 SQL（启动时自动执行）
            ├── data.sql                       ← 初始化数据 SQL（20支标的+默认组合）
            └── static/
                └── index.html                 ← 前端单页面
```

---

## API 接口一览

| 方法   | 路径                                  | 说明                     |
| ---- | ----------------------------------- | ---------------------- |
| GET  | `/api/portfolio/overview`           | 组合总览（总值/盈亏/收益率/资产分配饼图） |
| GET  | `/api/portfolio/weekly-performance` | 过去 7 天收益变化（柱状+折线图）     |
| GET  | `/api/portfolio/holdings`           | 当前持仓列表                 |
| GET  | `/api/stocks`                       | 全部可投资标的（20 支，含实时价格）    |
| GET  | `/api/stocks/{id}`                  | 单个标的详情                 |
| GET  | `/api/stocks/{id}/prices`           | 标的 7 天价格历史（悬停走势图）      |
| POST | `/api/portfolio/transactions`       | 买入/卖出交易                |

详细接口文档见 [API接口文档.md](./API接口文档.md)

---

## 数据库说明

| 表名                   | 说明                            |
| -------------------- | ----------------------------- |
| `stock`              | 投资标的（14 股票 + 4 债券 + 2 现金）     |
| `portfolio`          | 投资组合（系统默认 id=1，初始现金 $100,000） |
| `holding`            | 持仓记录（用户买入了哪些标的、多少数量）          |
| `transaction`        | 交易流水（每次买卖的记录）                 |
| `portfolio_snapshot` | 每日快照（用于历史收益曲线，当前为空时自动回算）      |

启动后 `data.sql` 会自动插入 20 支标的和默认组合，**无需手动导入数据**。

---

## 常见问题

### Q: 启动报 `MERGE INTO` 语法错误

`data.sql` 已改为 MySQL 兼容的 `INSERT IGNORE INTO`，确保本地代码是最新版本。

### Q: 启动报 `Communications link failure`

MySQL 没有启动，或 `application.properties` 里的地址/端口写错了。先确认 `mysql -u root -p` 能正常登录。

### Q: 启动报 `Access denied for user 'root'`

`application.properties` 里的密码和你本地 MySQL 密码不一致，改一下。

### Q: 前端页面空白，没有任何数据

按 F12 打开浏览器开发者工具，看 Console 里有没有报错。常见原因：

- 后端没有启动
- 后端端口不是 8080
- `/api/portfolio/overview` 返回了错误

### Q: IDEA 里代码飘红，但能正常运行

IDEA 索引问题，不影响编译。`File → Invalidate Caches → Invalidate and Restart` 可修复。

---

## 
