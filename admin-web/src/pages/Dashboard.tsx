import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getDashboardStats, getRecommendList, xxlLogin, xxlGetGroups, xxlGetJobList, xxlTriggerJob } from '../api/client'
import type { DashboardStats, RecommendStock, XxlJobInfo, XxlJobGroup } from '../types'
import {
  Row,
  Col,
  Card,
  Statistic,
  Table,
  Empty,
  Spin,
  Alert,
  Typography,
  Tag,
  Space,
  Tooltip,
  Progress,
  Button,
  Steps,
  Badge,
  Divider,
} from 'antd'
import {
  DashboardOutlined,
  StarOutlined,
  AppstoreOutlined,
  WarningOutlined,
  TrophyOutlined,
  RiseOutlined,
  FallOutlined,
  ThunderboltOutlined,
  SafetyCertificateOutlined,
  ArrowUpOutlined,
  FundOutlined,
  StockOutlined,
  FireOutlined,
  TeamOutlined,
  PlayCircleOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  LoadingOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'

const { Title, Text, Paragraph } = Typography

const NUM_FONT: React.CSSProperties = {
  fontFamily: "'BaiduNumberPlus', 'PingFang SC', 'Microsoft YaHei', -apple-system, sans-serif",
  fontVariantNumeric: 'tabular-nums',
  letterSpacing: '-0.02em',
}

const RISE_COLOR = '#cf1322'
const FALL_COLOR = '#3f8600'

const statCards = [
  {
    title: '今日推荐',
    dataIndex: 'recommendCount' as const,
    icon: <StarOutlined />,
    color: '#1677ff',
    bgColor: 'rgba(22, 119, 255, 0.08)',
    desc: '综合评分 >= 80',
    trend: 'up' as const,
  },
  {
    title: '评分股票',
    dataIndex: 'scoreCount' as const,
    icon: <StockOutlined />,
    color: '#722ed1',
    bgColor: 'rgba(114, 46, 209, 0.08)',
    desc: '四维模型评分',
    trend: 'up' as const,
  },
  {
    title: '行业覆盖',
    dataIndex: 'industryCount' as const,
    icon: <AppstoreOutlined />,
    color: '#fa8c16',
    bgColor: 'rgba(250, 140, 22, 0.08)',
    desc: '行业强度分析',
    trend: 'neutral' as const,
  },
  {
    title: '卖出信号',
    dataIndex: 'sellSignalCount' as const,
    icon: <WarningOutlined />,
    color: '#cf1322',
    bgColor: 'rgba(207, 19, 34, 0.08)',
    desc: '风险预警提示',
    trend: 'down' as const,
  },
]

function renderRank(rank: number) {
  if (rank === 1) return <Tag color="gold" style={NUM_FONT}>🥇 1</Tag>
  if (rank === 2) return <Tag color="default" style={{ ...NUM_FONT, color: '#7c7c7c', borderColor: '#d4d4d4' }}>🥈 2</Tag>
  if (rank === 3) return <Tag color="default" style={{ ...NUM_FONT, color: '#b87333', borderColor: '#e8c89e' }}>🥉 3</Tag>
  return <Text style={NUM_FONT}>{rank}</Text>
}

interface WorkflowStep {
  key: string
  title: string
  desc: string
  jobId?: number
  status: 'wait' | 'process' | 'finish' | 'error'
  handler: string
}

const WORKFLOW_STEPS: WorkflowStep[] = [
  { key: 'syncStock', title: '同步股票数据', desc: '同步日线/行业/财务', handler: 'syncStockDailyJob', status: 'wait' },
  { key: 'calcIndicator', title: '计算技术指标', desc: 'MA/RSI/MACD', handler: 'calcIndicatorsJob', status: 'wait' },
  { key: 'calcIndustry', title: '计算行业强度', desc: '行业排行', handler: 'calcIndustryStrengthJob', status: 'wait' },
  { key: 'calcScore', title: '计算综合评分', desc: '四维评分', handler: 'calcScoresJob', status: 'wait' },
  { key: 'generatePool', title: '生成推荐池', desc: '推荐股票+卖出信号', handler: 'generateRecommendPoolJob', status: 'wait' },
]

export default function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [recommend, setRecommend] = useState<RecommendStock[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [workflowSteps, setWorkflowSteps] = useState<WorkflowStep[]>(WORKFLOW_STEPS)
  const [workflowLoading, setWorkflowLoading] = useState(false)
  const [xxlConnected, setXxlConnected] = useState(false)

  useEffect(() => {
    Promise.all([getDashboardStats(), getRecommendList()])
      .then(([s, r]) => {
        setStats(s)
        setRecommend(r.slice(0, 5))
      })
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  // 加载 XXL-Job 任务状态
  const loadWorkflowStatus = async (retry = 3) => {
    try {
      const loginRes = await xxlLogin()
      if (loginRes.code !== 200) {
        if (retry > 0) {
          setTimeout(() => loadWorkflowStatus(retry - 1), 2000)
        } else {
          setXxlConnected(false)
        }
        return
      }
      setXxlConnected(true)

      const groupsRes = await xxlGetGroups()
      if (!groupsRes.data) return

      const allJobs: XxlJobInfo[] = []
      for (const group of groupsRes.data) {
        const jobsRes = await xxlGetJobList(group.id)
        if (jobsRes.data) {
          allJobs.push(...jobsRes.data)
        }
      }

      setWorkflowSteps(prev => prev.map(step => {
        const job = allJobs.find(j => j.executorHandler === step.handler)
        if (!job) return step
        let status: WorkflowStep['status'] = 'wait'
        if (job.triggerStatus === 1) status = 'finish'
        return { ...step, jobId: job.id, status }
      }))
    } catch (e) {
      console.error('XXL-Job load error:', e)
      if (retry > 0) {
        setTimeout(() => loadWorkflowStatus(retry - 1), 2000)
      } else {
        setXxlConnected(false)
      }
    }
  }

  useEffect(() => {
    const timer = setTimeout(() => loadWorkflowStatus(), 1000)
    const interval = setInterval(() => loadWorkflowStatus(0), 30000)
    return () => { clearTimeout(timer); clearInterval(interval) }
  }, [])

  // 执行每日任务
  const runDailyWorkflow = async () => {
    setWorkflowLoading(true)
    try {
      await xxlLogin()
      for (const step of workflowSteps) {
        if (step.jobId) {
          setWorkflowSteps(prev => prev.map(s => s.key === step.key ? { ...s, status: 'process' } : s))
          await xxlTriggerJob(step.jobId)
          await new Promise(r => setTimeout(r, 2000))
          setWorkflowSteps(prev => prev.map(s => s.key === step.key ? { ...s, status: 'finish' } : s))
        }
      }
    } catch (e) {
      setError('工作流执行失败: ' + (e as Error).message)
    } finally {
      setWorkflowLoading(false)
      loadWorkflowStatus()
    }
  }

  const columns: ColumnsType<RecommendStock> = [
    {
      title: '排名',
      dataIndex: 'rank',
      width: 70,
      render: (rank: number) => renderRank(rank),
    },
    {
      title: '代码',
      dataIndex: 'code',
      width: 110,
      render: (code: string) => (
        <Space>
          <FundOutlined style={{ color: '#1677ff', fontSize: 14 }} />
          <Text style={{ ...NUM_FONT, fontSize: 14, whiteSpace: 'nowrap' }}>{code}</Text>
        </Space>
      ),
    },
    {
      title: '名称',
      dataIndex: 'name',
      width: 100,
      render: (name: string) => <Text strong style={{ whiteSpace: 'nowrap' }}>{name}</Text>,
    },
    {
      title: '现价',
      dataIndex: 'currentPrice',
      width: 90,
      align: 'right',
      render: (price: number) => (
        <Text style={{ ...NUM_FONT, fontSize: 14, fontWeight: 600 }}>
          {price ? price.toFixed(2) : '--'}
        </Text>
      ),
    },
    {
      title: '涨跌',
      dataIndex: 'changePercent',
      width: 80,
      align: 'right',
      render: (pct: number) => {
        if (pct == null) return <Text>--</Text>
        const color = pct >= 0 ? RISE_COLOR : FALL_COLOR
        const icon = pct >= 0 ? <RiseOutlined /> : <FallOutlined />
        return (
          <Tag color={pct >= 0 ? 'red' : 'green'} style={{ fontWeight: 700, borderRadius: 4, fontSize: 12 }}>
            {icon} {pct >= 0 ? '+' : ''}{pct.toFixed(2)}%
          </Tag>
        )
      },
    },
    {
      title: '目标价',
      dataIndex: 'targetPrice',
      width: 90,
      align: 'right',
      render: (price: number, record: RecommendStock) => {
        const current = record.currentPrice || 0
        const target = price || 0
        const gain = current > 0 ? ((target - current) / current * 100) : 0
        return (
          <Tooltip title={`预期收益 +${gain.toFixed(1)}%`}>
            <Text style={{ ...NUM_FONT, fontSize: 13, color: '#1677ff', fontWeight: 600 }}>
              {price ? price.toFixed(2) : '--'}
            </Text>
          </Tooltip>
        )
      },
    },
    {
      title: '综合评分',
      dataIndex: 'totalScore',
      width: 100,
      render: (score: number) => (
        <Tooltip title={`${score}/100`}>
          <Progress
            type="circle"
            size={40}
            percent={score}
            format={() => (
              <span style={{ ...NUM_FONT, fontSize: 12, color: score >= 90 ? RISE_COLOR : '#1677ff' }}>
                {score}
              </span>
            )}
            strokeColor={score >= 90 ? RISE_COLOR : '#1677ff'}
            trailColor="#f0f0f0"
          />
        </Tooltip>
      ),
    },
    {
      title: '策略',
      width: 120,
      render: (_: any, record: RecommendStock) => (
        <Tooltip title={record.strategyDesc || '买入持有策略'}>
          <Space direction="vertical" size={0} style={{ lineHeight: 1.6 }}>
            <Tag color="blue" style={{ fontSize: 11, borderRadius: 4, margin: 0 }}>
              持{record.holdDays || '--'}天
            </Tag>
            <Text type="secondary" style={{ fontSize: 11, whiteSpace: 'nowrap' }}>
              {record.buyTime || '--'} 买
            </Text>
            <Text type="secondary" style={{ fontSize: 11, whiteSpace: 'nowrap' }}>
              {record.sellTime || '--'} 卖
            </Text>
          </Space>
        </Tooltip>
      ),
    },
    {
      title: '信号',
      width: 70,
      render: () => (
        <Tag color="red" style={{ fontWeight: 700, borderRadius: 6, fontSize: 12 }}>
          <RiseOutlined style={{ marginRight: 2 }} />BUY
        </Tag>
      ),
    },
  ]

  return (
    <div className="page-container">
      {/* 页面标题 */}
      <div style={{ marginBottom: 24 }}>
        <Space align="center" size={12}>
          <div style={{
            width: 40, height: 40, borderRadius: 10,
            background: 'linear-gradient(135deg, #1677ff, #4096ff)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <DashboardOutlined style={{ fontSize: 20, color: '#fff' }} />
          </div>
          <div>
            <Title level={4} style={{ margin: 0 }}>仪表盘</Title>
            <Text type="secondary" style={{ fontSize: 13 }}>
              数据日期：{stats?.latestTradeDate || '暂无数据'}
            </Text>
          </div>
        </Space>
      </div>

      {error && (
        <Alert
          message="加载失败"
          description={error}
          type="error"
          showIcon
          style={{ marginBottom: 24, borderRadius: 8 }}
        />
      )}

      <Spin spinning={loading}>
        {/* 统计卡片 */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          {statCards.map(card => (
            <Col xs={24} sm={12} lg={6} key={card.title}>
              <div className="stat-card-wrapper">
                <Card hoverable bodyStyle={{ padding: '20px 24px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div>
                      <Statistic
                        title={
                          <Text type="secondary" style={{ fontSize: 13 }}>
                            {card.title}
                          </Text>
                        }
                        value={stats?.[card.dataIndex] ?? 0}
                        styles={{
                          content: {
                            ...NUM_FONT,
                            fontSize: 28,
                            fontWeight: 800,
                            color: card.color,
                          },
                        }}
                      />
                      <div style={{ fontSize: 12, color: '#999', marginTop: 8, display: 'flex', alignItems: 'center', gap: 4 }}>
                        {(stats?.[card.dataIndex] ?? 0) > 0 && card.trend === 'up' && <ArrowUpOutlined style={{ color: '#3f8600', fontSize: 10 }} />}
                        {(stats?.[card.dataIndex] ?? 0) === 0 && <span style={{ color: '#bbb' }}>—</span>}
                        {(stats?.[card.dataIndex] ?? 0) > 0 && card.desc}
                      </div>
                    </div>
                    <div style={{
                      width: 48, height: 48, borderRadius: 12,
                      background: card.bgColor,
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                    }}>
                      <span style={{ color: card.color, fontSize: 22 }}>{card.icon}</span>
                    </div>
                  </div>
                </Card>
              </div>
            </Col>
          ))}
        </Row>

        {/* 今日推荐 TOP 5 */}
        <div className="table-card">
          <Card
            title={
              <Space size={8}>
                <TrophyOutlined style={{ color: '#faad14' }} />
                <span style={{ fontWeight: 600 }}>今日推荐 TOP 5</span>
                <Tag color="blue" style={{ marginLeft: 4, borderRadius: 6 }}>
                  <FireOutlined /> 精选
                </Tag>
              </Space>
            }
            extra={
              <Link to="/recommend" style={{ color: '#1677ff', fontSize: 13 }}>
                查看全部 →
              </Link>
            }
          >
            {recommend.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <Space direction="vertical" size={4}>
                    <Text type="secondary">暂无推荐数据</Text>
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      请先运行「数据同步」和「评分任务」，系统将自动筛选综合评分 &gt;= 80 的优质股票
                    </Text>
                  </Space>
                }
              />
            ) : (
              <Table<RecommendStock>
                dataSource={recommend}
                columns={columns}
                rowKey="code"
                pagination={false}
                size="middle"
              />
            )}
          </Card>
        </div>

        {/* 数据工作流 */}
        <Card
          style={{ marginTop: 16, borderRadius: 12 }}
          title={
            <Space size={8}>
              <SyncOutlined style={{ color: '#1677ff' }} />
              <span style={{ fontWeight: 600 }}>每日数据工作流</span>
              <Badge
                status={xxlConnected ? 'success' : 'error'}
                text={xxlConnected ? 'XXL-Job 已连接' : 'XXL-Job 未连接'}
              />
            </Space>
          }
          extra={
            <Button
              type="primary"
              icon={workflowLoading ? <LoadingOutlined /> : <PlayCircleOutlined />}
              onClick={runDailyWorkflow}
              loading={workflowLoading}
              disabled={!xxlConnected}
            >
              {workflowLoading ? '执行中...' : '执行每日任务'}
            </Button>
          }
        >
          <Steps
            direction="horizontal"
            size="small"
            current={workflowSteps.filter(s => s.status === 'finish').length}
            items={workflowSteps.map(step => ({
              title: step.title,
              description: step.desc,
              status: step.status,
              icon: step.status === 'finish' ? <CheckCircleOutlined /> :
                    step.status === 'process' ? <LoadingOutlined /> :
                    step.status === 'error' ? <CloseCircleOutlined /> :
                    <ClockCircleOutlined />,
            }))}
          />
          <Divider style={{ margin: '16px 0' }} />
          <Space wrap>
            {workflowSteps.map(step => (
              <Tag
                key={step.key}
                color={step.status === 'finish' ? 'success' : step.status === 'process' ? 'processing' : 'default'}
                icon={step.status === 'finish' ? <CheckCircleOutlined /> : step.status === 'process' ? <LoadingOutlined /> : null}
              >
                {step.title}
              </Tag>
            ))}
          </Space>
        </Card>

        {/* 快捷入口 */}
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24} sm={8}>
            <Card size="small" hoverable bodyStyle={{ padding: '16px 20px' }}>
              <Space>
                <ThunderboltOutlined style={{ fontSize: 20, color: '#1677ff' }} />
                <div>
                  <Text strong>快速评分</Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 12 }}>对单只股票进行四维评分</Text>
                </div>
              </Space>
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card size="small" hoverable bodyStyle={{ padding: '16px 20px' }}>
              <Space>
                <SafetyCertificateOutlined style={{ fontSize: 20, color: '#3f8600' }} />
                <div>
                  <Text strong>风险预警</Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 12 }}>查看持仓股票卖出信号</Text>
                </div>
              </Space>
            </Card>
          </Col>
          <Col xs={24} sm={8}>
            <Card size="small" hoverable bodyStyle={{ padding: '16px 20px' }}>
              <Space>
                <TeamOutlined style={{ fontSize: 20, color: '#722ed1' }} />
                <div>
                  <Text strong>行业分析</Text>
                  <br />
                  <Text type="secondary" style={{ fontSize: 12 }}>行业强度排行与趋势</Text>
                </div>
              </Space>
            </Card>
          </Col>
        </Row>
      </Spin>
    </div>
  )
}
