# XXL-JOB 定时任务配置参考
# 在 XXL-JOB Admin (http://localhost:8088/xxl-job-admin) 中配置以下任务

# | 任务名称 | Cron | 执行器 | JobHandler |
# |---------|------|--------|------------|
# | 同步股票数据 | 0 30 15 * * ? | market-data-service | syncStockDailyJob |
# | 同步行业数据 | 0 40 15 * * ? | market-data-service | syncIndustryJob |
# | 同步财务数据 | 0 50 15 * * ? | market-data-service | syncFinanceJob |
# | 计算技术指标 | 0 0 16 * * ? | factor-engine-service | calcIndicatorsJob |
# | 计算行业强度 | 0 10 16 * * ? | factor-engine-service | calcIndustryStrengthJob |
# | 计算综合评分 | 0 20 16 * * ? | factor-engine-service | calcScoresJob |
# | 生成推荐池 | 0 30 16 * * ? | strategy-service | generateRecommendPoolJob |
# | 全量初始化同步 | 手动触发 | market-data-service | initFullSyncJob |
