import { useEffect, useState } from 'react'
import { getRecommendList } from '../api/client'
import type { RecommendStock } from '../types'
import {
  Card,
  Table,
  Tag,
  Button,
  DatePicker,
  Space,
  Empty,
  Spin,
  Alert,
  Typography,
  Progress,
  Tooltip,
} from 'antd'
import {
  SearchOutlined,
  StarOutlined,
  RiseOutlined,
  FallOutlined,
  FilterOutlined,
  StarFilled,
  TrophyOutlined,
  FireOutlined,
  ClockCircleOutlined,
  AimOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const { Title, Text, Paragraph } = Typography

const NUM_FONT: React.CSSProperties = {
  fontFamily: "'BaiduNumberPlus', 'PingFang SC', 'Microsoft YaHei', -apple-system, sans-serif",
  fontVariantNumeric: 'tabular-nums',
  letterSpacing: '-0.02em',
}

const RISE_COLOR = '#cf1322'
const FALL_COLOR = '#3f8600'

function renderRank(rank: number) {
  if (rank === 1) return <Tag color="gold" style={NUM_FONT}>🥇 1</Tag>
  if (rank === 2) return <Tag color="default" style={{ ...NUM_FONT, color: '#7c7c7c', borderColor: '#d4d4d4' }}>🥈 2</Tag>
  if (rank === 3) return <Tag color="default" style={{ ...NUM_FONT, color: '#b87333', borderColor: '#e8c89e' }}>🥉 3</Tag>
  return <Text style={NUM_FONT}>{rank}</Text>
}

function getScoreColor(score: number): string {
  if (score >= 90) return RISE_COLOR
  if (score >= 80) return '#1677ff'
  return '#999'
}

function getScoreLevel(score: number): { text: string; color: string } {
  if (score >= 95) return { text: '卓越', color: '#cf1322' }
  if (score >= 90) return { text: '优秀', color: '#fa8c16' }
  if (score >= 80) return { text: '良好', color: '#1677ff' }
  return { text: '一般', color: '#999' }
}

export default function RecommendPool() {
  const [data, setData] = useState<RecommendStock[]>([])
  const [date, setDate] = useState<dayjs.Dayjs | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = () => {
    setLoading(true)
    setError('')
    getRecommendList(date?.format('YYYY-MM-DD') || undefined)
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const columns: ColumnsType<RecommendStock> = [
    {
      title: '排名',
      dataIndex: 'rank',
      width: 70,
      fixed: 'left',
      render: (rank: number) => renderRank(rank),
    },
    {
      title: '代码',
      dataIndex: 'code',
      width: 110,
      fixed: 'left',
      render: (code: string, record: RecommendStock) => (
        <Space>
          <StarFilled style={{ color: '#faad14', fontSize: 14 }} />
          <a
            href={`https://finance.baidu.com/stock/ab-${code}?name=${encodeURIComponent(record.name || '')}`}
            target="_blank"
            rel="noopener noreferrer"
            style={{ ...NUM_FONT, fontSize: 14, whiteSpace: 'nowrap' }}
          >
            {code}
          </a>
        </Space>
      ),
    },
    {
      title: '名称',
      dataIndex: 'name',
      width: 100,
      fixed: 'left',
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
      width: 85,
      align: 'right',
      render: (pct: number) => {
        if (pct == null) return <Text>--</Text>
        return (
          <Tag color={pct >= 0 ? 'red' : 'green'} style={{ fontWeight: 700, borderRadius: 4, fontSize: 12 }}>
            {pct >= 0 ? <RiseOutlined /> : <FallOutlined />}
            {pct >= 0 ? '+' : ''}{pct.toFixed(2)}%
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
          <Tooltip title={`预期收益 +${gain.toFixed(1)}% · ${record.strategyDesc || ''}`}>
            <div style={{ textAlign: 'right' }}>
              <Text style={{ ...NUM_FONT, fontSize: 14, color: '#1677ff', fontWeight: 600 }}>
                {price ? price.toFixed(2) : '--'}
              </Text>
              <div>
                <Text style={{ fontSize: 11, color: gain > 0 ? RISE_COLOR : '#999' }}>
                  +{gain.toFixed(1)}%
                </Text>
              </div>
            </div>
          </Tooltip>
        )
      },
    },
    {
      title: '综合评分',
      dataIndex: 'totalScore',
      width: 180,
      render: (score: number) => {
        const level = getScoreLevel(score)
        return (
          <Space align="center" size={12}>
            <Tooltip title={`${score}/100 · ${level.text}`}>
              <Progress
                type="circle"
                size={44}
                percent={score}
                format={() => (
                  <span style={{ ...NUM_FONT, fontSize: 13, color: getScoreColor(score) }}>
                    {score}
                  </span>
                )}
                strokeColor={getScoreColor(score)}
                trailColor="#f0f0f0"
              />
            </Tooltip>
            <div style={{ flex: 1, minWidth: 80 }}>
              <Progress
                percent={score}
                showInfo={false}
                strokeColor={getScoreColor(score)}
                size="small"
              />
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 2 }}>
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {score}/100
                </Text>
                <Tag color={level.color} style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px', margin: 0 }}>
                  {level.text}
                </Tag>
              </div>
            </div>
          </Space>
        )
      },
    },
    {
      title: '交易策略',
      width: 140,
      render: (_: any, record: RecommendStock) => (
        <Tooltip title={record.strategyDesc || '买入持有策略'}>
          <Space direction="vertical" size={2} style={{ lineHeight: 1.5 }}>
            <Space size={4}>
              <ClockCircleOutlined style={{ color: '#1677ff', fontSize: 12 }} />
              <Tag color="blue" style={{ fontSize: 11, borderRadius: 4, margin: 0, padding: '0 6px' }}>
                持有 {record.holdDays || '--'} 天
              </Tag>
            </Space>
            <Space size={4}>
              <AimOutlined style={{ color: '#52c41a', fontSize: 12 }} />
              <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                {record.buyTime || '--'} 买入
              </Text>
            </Space>
            <Space size={4}>
              <RiseOutlined style={{ color: RISE_COLOR, fontSize: 12 }} />
              <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                {record.sellTime || '--'} 卖出
              </Text>
            </Space>
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
    {
      title: '日期',
      dataIndex: 'tradeDate',
      width: 100,
      render: (tradeDate: string) => (
        <Text type="secondary" style={{ fontSize: 13 }}>{tradeDate}</Text>
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
            background: 'linear-gradient(135deg, #faad14, #ffc53d)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <StarOutlined style={{ fontSize: 20, color: '#fff' }} />
          </div>
          <div>
            <Title level={4} style={{ margin: 0 }}>推荐股票池</Title>
            <Text type="secondary" style={{ fontSize: 13 }}>
              综合评分 &gt;= 80 的前 10 只股票
            </Text>
          </div>
        </Space>
      </div>

      {/* 工具栏 */}
      <Space style={{ marginBottom: 16 }} wrap>
        <DatePicker
          value={date}
          onChange={d => setDate(d)}
          placeholder="选择日期"
          allowClear
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={load}>
          查询
        </Button>
        <Button icon={<FilterOutlined />}>
          高级筛选
        </Button>
      </Space>

      {error && (
        <Alert
          message="加载失败"
          description={error}
          type="error"
          showIcon
          style={{ marginBottom: 16, borderRadius: 8 }}
        />
      )}

      <div className="table-card">
        <Card
          title={
            <Space size={8}>
              <TrophyOutlined style={{ color: '#faad14' }} />
              <span style={{ fontWeight: 600 }}>推荐列表</span>
              <Tag color="blue" style={{ borderRadius: 6 }}>
                <FireOutlined /> {data.length} 只
              </Tag>
            </Space>
          }
        >
          <Spin spinning={loading}>
            {data.length === 0 && !loading ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <Space direction="vertical" size={4}>
                    <Text type="secondary">暂无推荐数据</Text>
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      当前筛选条件下没有符合条件的股票，请尝试切换日期或调整评分阈值
                    </Text>
                  </Space>
                }
              />
            ) : (
              <Table<RecommendStock>
                dataSource={data}
                columns={columns}
                rowKey="code"
                pagination={false}
                size="middle"
              />
            )}
          </Spin>
        </Card>
      </div>
    </div>
  )
}
