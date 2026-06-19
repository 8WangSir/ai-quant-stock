import { useEffect, useState } from 'react'
import { getIndustryRank } from '../api/client'
import type { IndustryRank } from '../types'
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
  RiseOutlined,
  FallOutlined,
  AppstoreOutlined,
  TrophyOutlined,
  FireOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  MinusOutlined,
  FilterOutlined,
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

function formatPct(v: number | null | undefined) {
  if (v == null) return <Text type="secondary">-</Text>
  const pct = (v * 100).toFixed(2)
  const num = parseFloat(pct)
  const Icon = num >= 0 ? ArrowUpOutlined : ArrowDownOutlined
  return (
    <Space size={4}>
      <Icon style={{ fontSize: 10, color: num >= 0 ? RISE_COLOR : FALL_COLOR }} />
      <Text style={{ ...NUM_FONT, color: num >= 0 ? RISE_COLOR : FALL_COLOR }}>
        {num >= 0 ? '+' : ''}{pct}%
      </Text>
    </Space>
  )
}

function renderRank(rank: number) {
  if (rank === 1) return <Tag color="gold" style={NUM_FONT}>🥇 1</Tag>
  if (rank === 2) return <Tag color="default" style={{ ...NUM_FONT, color: '#7c7c7c', borderColor: '#d4d4d4' }}>🥈 2</Tag>
  if (rank === 3) return <Tag color="default" style={{ ...NUM_FONT, color: '#b87333', borderColor: '#e8c89e' }}>🥉 3</Tag>
  return <Text style={NUM_FONT}>{rank}</Text>
}

function getStrengthColor(strength: number): string {
  if (strength > 0.1) return RISE_COLOR
  if (strength > 0) return '#1677ff'
  if (strength > -0.05) return '#fa8c16'
  return FALL_COLOR
}

function getStrengthIcon(strength: number) {
  if (strength > 0.05) return <FireOutlined style={{ color: RISE_COLOR }} />
  if (strength > 0) return <RiseOutlined style={{ color: '#1677ff' }} />
  if (strength > -0.05) return <MinusOutlined style={{ color: '#fa8c16' }} />
  return <FallOutlined style={{ color: FALL_COLOR }} />
}

export default function IndustryRankPage() {
  const [data, setData] = useState<IndustryRank[]>([])
  const [date, setDate] = useState<dayjs.Dayjs | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = () => {
    setLoading(true)
    setError('')
    getIndustryRank(date?.format('YYYY-MM-DD') || undefined)
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const maxAbsStrength = Math.max(...data.map(d => Math.abs(d.strength ?? 0)), 0.01)

  const columns: ColumnsType<IndustryRank> = [
    {
      title: '排名',
      dataIndex: 'rank',
      width: 80,
      render: (rank: number) => renderRank(rank),
    },
    {
      title: '行业',
      dataIndex: 'industryName',
      render: (name: string) => (
        <Space>
          <AppstoreOutlined style={{ color: '#1677ff', fontSize: 14 }} />
          <Text strong>{name}</Text>
        </Space>
      ),
    },
    {
      title: '强度',
      dataIndex: 'strength',
      width: 120,
      render: (strength: number) => {
        if (strength == null) return <Text type="secondary">-</Text>
        return (
          <Space size={6}>
            {getStrengthIcon(strength)}
            <Text style={{ ...NUM_FONT, fontSize: 16, color: getStrengthColor(strength) }}>
              {(strength * 100).toFixed(2)}%
            </Text>
          </Space>
        )
      },
    },
    {
      title: '强度指示',
      dataIndex: 'strength',
      width: 180,
      render: (strength: number) => {
        if (strength == null) return null
        const barWidth = Math.min(Math.abs(strength) / maxAbsStrength * 100, 100)
        const barColor = getStrengthColor(strength)
        return (
          <Tooltip title={`绝对强度: ${(Math.abs(strength) * 100).toFixed(2)}%`}>
            <Progress
              percent={barWidth}
              showInfo={false}
              strokeColor={barColor}
              size="small"
            />
          </Tooltip>
        )
      },
    },
    {
      title: '得分',
      dataIndex: 'score',
      width: 80,
      render: (score: number) => <Text style={{ ...NUM_FONT, fontSize: 16 }}>{score}</Text>,
    },
    {
      title: '20日涨幅',
      dataIndex: 'return20d',
      render: (v: number) => formatPct(v),
    },
    {
      title: '60日涨幅',
      dataIndex: 'return60d',
      render: (v: number) => formatPct(v),
    },
    {
      title: '120日涨幅',
      dataIndex: 'return120d',
      render: (v: number) => formatPct(v),
    },
  ]

  return (
    <div className="page-container">
      {/* 页面标题 */}
      <div style={{ marginBottom: 24 }}>
        <Space align="center" size={12}>
          <div style={{
            width: 40, height: 40, borderRadius: 10,
            background: 'linear-gradient(135deg, #fa8c16, #ffa940)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <AppstoreOutlined style={{ fontSize: 20, color: '#fff' }} />
          </div>
          <div>
            <Title level={4} style={{ margin: 0 }}>行业强度排行</Title>
            <Text type="secondary" style={{ fontSize: 13 }}>
              强度 = 20日涨幅 × 0.4 + 60日涨幅 × 0.4 + 120日涨幅 × 0.2
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
              <span style={{ fontWeight: 600 }}>行业排行</span>
              <Tag color="orange" style={{ borderRadius: 6 }}>
                <FireOutlined /> {data.length} 个行业
              </Tag>
            </Space>
          }
          extra={
            <Space size={12} style={{ fontSize: 12 }}>
              <Text style={{ color: RISE_COLOR }}>
                <RiseOutlined /> 红 = 涨
              </Text>
              <Text style={{ color: FALL_COLOR }}>
                <FallOutlined /> 绿 = 跌
              </Text>
            </Space>
          }
        >
          <Spin spinning={loading}>
            {data.length === 0 && !loading ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <Space direction="vertical" size={4}>
                    <Text type="secondary">暂无行业数据</Text>
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      请先运行行业数据同步任务
                    </Text>
                  </Space>
                }
              />
            ) : (
              <Table<IndustryRank>
                dataSource={data}
                columns={columns}
                rowKey="industryName"
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
