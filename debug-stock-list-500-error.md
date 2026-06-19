# Debug Session: stock-list-500-error

## Status: [CLOSED] ✅

## Symptom
调用 `POST /api/internal/sync/stock-list` 接口返回 500 内部服务器错误。

## Environment
- OS: Windows
- Java: 21.0.11
- Spring Boot: 3.2.5
- Service: market-data-service (port 8081)

## Root Cause (确认)
**H3 被证实：数据库连接失败**

完整堆栈：
```
Caused by: org.postgresql.util.PSQLException: Connection to localhost:15432 refused.
```

问题分析：
1. `syncStockList()` 方法使用 `@Transactional` 注解
2. Spring 启动事务时尝试从 HikariCP 连接池获取数据库连接
3. PostgreSQL 在 `localhost:15432` 没有运行
4. 连接被拒绝，导致 500 Internal Server Error

## Solution
1. 使用 docker-compose 启动基础设施：
   ```bash
   docker-compose up -d postgres redis
   ```
2. 容器映射端口到本地：
   - postgres: `localhost:15432` → `container:5432`
   - redis: `localhost:6379` → `container:6379`
3. 本地运行 market-data-service（使用 local profile）

## Result ✅
- 接口 `POST /api/internal/sync/stock-list` 正常返回 `股票列表同步已启动`
- 业务逻辑正常执行（无错误）
