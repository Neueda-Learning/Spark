# Spark Portfolio Manager

Spring Boot + MySQL 投资组合演示项目。生产默认从数据库读取 Yahoo Finance
盘后日行情，`demo`/`test` profile 使用确定性的模拟行情。

## 初始化数据库行情

首次运行前请先确保 MySQL 可用，并通过环境或本地配置提供数据库连接信息。
日行情表由 `src/main/resources/schema.sql` 自动创建。

首次历史回填默认关闭。如需在本次启动时回填最近 18 个月行情：

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments=--market-data.backfill-on-startup=true
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
GET /api/stocks/{id}/candles?interval=WEEKLY&weeks=52
```

投资操作页面中点击非 CASH 标的行会打开周 K 弹窗。买入和卖出仍使用数据库中
最新可用的盘后收盘价，不代表实时成交价。

## 验证

```bash
mvn test
```
