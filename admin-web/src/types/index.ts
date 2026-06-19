export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface RecommendStock {
  code: string
  name: string
  tradeDate: string
  totalScore: number
  rank: number
  industry?: string
  close?: number
  // 新增字段
  changePercent?: number
  currentPrice?: number
  targetPrice?: number
  holdDays?: number
  buyTime?: string
  sellTime?: string
  strategyDesc?: string
}

export interface StockScore {
  code: string
  name?: string
  tradeDate: string
  financeScore: number
  trendScore: number
  capitalScore: number
  industryScore: number
  totalScore: number
}

export interface IndustryRank {
  industryName: string
  tradeDate: string
  strength: number
  rank: number
  score: number
  return20d: number
  return60d: number
  return120d: number
}

export interface TradeSignal {
  code: string
  name?: string
  tradeDate: string
  signalType: string
  reason: string
}

export interface DashboardStats {
  recommendCount: number
  scoreCount: number
  industryCount: number
  sellSignalCount: number
  latestTradeDate: string
}

export interface XxlJobInfo {
  id: number
  jobDesc: string
  executorHandler: string
  scheduleType: string
  scheduleConf: string
  triggerStatus: number
  triggerLastTime: number
  triggerNextTime: number
}

export interface XxlJobGroup {
  id: number
  appname: string
  title: string
  registryList?: string[]
}
