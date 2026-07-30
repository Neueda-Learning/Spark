# Spark Portfolio Manager

Spring Boot + MySQL 投资组合演示项目。生产默认从数据库读取 Yahoo Finance
盘后日行情，`demo`/`test` profile 使用确定性的模拟行情。前端由 Spring Boot
直接提供，包含组合总览、投资操作、K 线图和 AI 投资助手。

## 运行要求

- JDK 21
- Maven 3.8+
- MySQL 8.0+

## 配置

应用会读取项目根目录下可选的 `.env` 文件，也可以直接使用环境变量：

```properties
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/portfoliodb?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your-password

AI_PROVIDER=qwen
AI_CHAT_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
AI_MODEL=qwen-plus
AI_API_KEY=your-api-key
```

`.env` 已被 Git 忽略。不要将数据库密码或 AI API Key 提交到仓库。

## 启动

```bash
mvn spring-boot:run
```

启动后访问 <http://localhost:8080>。

## 初始化数据库行情

首次运行前请先确保 MySQL 可用，并通过环境或本地配置提供数据库连接信息。
日行情表由 `src/main/resources/schema.sql` 自动创建。

首次历史回填默认开启。新库或历史覆盖不足时会补齐最近 60 个月行情；已有完整
历史时只同步最近 10 天的重叠区间。

如需临时关闭启动回填：

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments=--market-data.backfill-on-startup=false
```

回填过程按股票隔离失败，并通过 `(stock_id, trade_date)` 唯一键幂等更新。
正常运行时，应用会在纽约时间工作日 18:30 同步盘后日线，并在 08:00
执行补偿同步。

## 演示模式

无需真实行情时可启用模拟价格：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

## 周 K API

```http
GET /api/stocks/{id}/candles?interval=WEEKLY&limit=52
```

投资操作页面中点击非 CASH 标的行会打开周 K 弹窗。买入和卖出仍使用数据库中
最新可用的盘后收盘价，不代表实时成交价。

## AI 投资助手

助手支持普通和流式响应：

```http
POST /api/portfolio/ai-assistant/chat
POST /api/portfolio/ai-assistant/chat/stream
```

默认使用系统内部行情作为上下文。如需让助手直接查询 Yahoo Finance，可设置：

```properties
AI_MARKET_DATA_MODE=yahoo
```

## 验证

```bash
mvn test
```
