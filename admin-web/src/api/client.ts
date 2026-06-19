import type { ApiResponse, DashboardStats, IndustryRank, RecommendStock, StockScore, TradeSignal, XxlJobInfo, XxlJobGroup } from '../types'

async function fetchApi<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(path, options)
  const json: ApiResponse<T> = await res.json()
  if (json.code !== 200) {
    throw new Error(json.message || '请求失败')
  }
  return json.data
}

export function getDashboardStats(date?: string) {
  const q = date ? `?date=${date}` : ''
  return fetchApi<DashboardStats>(`/api/dashboard/stats${q}`)
}

export function getRecommendList(date?: string) {
  const q = date ? `?date=${date}` : ''
  return fetchApi<RecommendStock[]>(`/api/recommend/list${q}`)
}

export function getScoreList(date?: string, minScore = 0) {
  const params = new URLSearchParams()
  if (date) params.set('date', date)
  if (minScore > 0) params.set('minScore', String(minScore))
  const q = params.toString() ? `?${params}` : ''
  return fetchApi<StockScore[]>(`/api/score/list${q}`)
}

export function getStockScore(code: string, date?: string) {
  const q = date ? `?date=${date}` : ''
  return fetchApi<StockScore>(`/api/stock/score/${code}${q}`)
}

export function getIndustryRank(date?: string) {
  const q = date ? `?date=${date}` : ''
  return fetchApi<IndustryRank[]>(`/api/industry/rank${q}`)
}

export function getSellSignals(date?: string) {
  const q = date ? `?date=${date}` : ''
  return fetchApi<TradeSignal[]>(`/api/signal/list${q}`)
}

export function getStockSignals(code: string, date?: string) {
  const q = date ? `?date=${date}` : ''
  return fetchApi<TradeSignal[]>(`/api/signal/${code}${q}`)
}

// XXL-Job API
const XXL_JOB_BASE = '/xxl-job-admin'

async function xxlFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${XXL_JOB_BASE}${path}`, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      ...options?.headers,
    },
  })
  return res.json()
}

export function xxlLogin() {
  return xxlFetch<{ code: number }>('/login', {
    method: 'POST',
    body: 'userName=admin&password=123456',
  })
}

export function xxlGetJobList(jobGroup: number) {
  return xxlFetch<{ code?: number; data?: XxlJobInfo[] }>('/jobinfo/pageList', {
    method: 'POST',
    body: `jobGroup=${jobGroup}&triggerStatus=-1&jobDesc=&executorHandler=&author=&start=0&length=100`,
  })
}

export function xxlGetGroups() {
  return xxlFetch<{ code: number; data?: XxlJobGroup[] }>('/jobgroup/pageList', {
    method: 'POST',
    body: 'start=0&length=100',
  })
}

export function xxlTriggerJob(jobId: number) {
  return xxlFetch<{ code: number }>('/jobinfo/trigger', {
    method: 'POST',
    body: `id=${jobId}&executorParam=`,
  })
}

export function xxlStartJob(jobId: number) {
  return xxlFetch<{ code: number }>('/jobinfo/start', {
    method: 'POST',
    body: `id=${jobId}`,
  })
}

export function xxlStopJob(jobId: number) {
  return xxlFetch<{ code: number }>('/jobinfo/stop', {
    method: 'POST',
    body: `id=${jobId}`,
  })
}
