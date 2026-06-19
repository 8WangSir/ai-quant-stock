import { useEffect, useState } from 'react'
import { getScoreList, getStockScore } from '../api/client'
import type { StockScore } from '../types'
import {
  Card,
  Table,
  Button,
  Input,
  Select,
  DatePicker,
  Space,
  Empty,
  Spin,
  Alert,
  Typography,
  Progress,
  Row,
  Col,
  Tag,
  Tooltip,
} from 'antd'
import {
  SearchOutlined,
  BarChartOutlined,
  CloseOutlined,
  FundOutlined,
  LineChartOutlined,
  DollarOutlined,
  AppstoreOutlined,
  InfoCircleOutlined,
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

const DIMENSIONS = [
  { key: 'finance', label: '基本面', color: '#4A6CF7', icon: <FundOutlined /> },
  { key: 'trend', label: '技术面', color: '#8b5cf6', icon: <LineChartOutlined /> },
  { key: 'capital', label: '资金面', color: '#3f8600', icon: <DollarOutlined /> },
  { key: 'industry', label: '行业面', color: '#F39C12', icon: <AppstoreOutlined /> },
] as const

function getScoreColor(score: number): string {
  if (score >= 80) return '#1677ff'
  if (score >= 60) return '#333'
  return '#999'
}

function ScoreBar({ score }: { score: StockScore }) {
  const total = score.financeScore + score.trendScore + score.capitalScore + score.industryScore
  if (total === 0) return null
  return (
    <Tooltip title={`基本面${score.financeScore} 技术面${score.trendScore} 资金面${score.capitalScore} 行业面${score.industryScore}`}>
      <div style={{ display: 'flex', width: 120, height: 8, borderRadius: 4, overflow: 'hidden', background: '#f0f0f0', cursor: 'pointer' }}>
        <div style={{ width: `${(score.financeScore / total) * 100}%`, background: '#4A6CF7' }} />
        <div style={{ width: `${(score.trendScore / total) * 100}%`, background: '#8b5cf6' }} />
        <div style={{ width: `${(score.capitalScore / total) * 100}%`, background: '#3f8600' }} />
        <div style={{ width: `${(score.industryScore / total) * 100}%`, background: '#F39C12' }} />
      </div>
    </Tooltip>
  )
}

function ScoreDetailCard({ score, onClose }: { score: StockScore; onClose: () => void }) {
  const values = [
    score.financeScore,
    score.trendScore,
    score.capitalScore,
    score.industryScore,
  ]

  return (
    <Card
      style={{ marginBottom: 20, borderRadius: 12 }}
      title={
        <Space>
          <BarChartOutlined style={{ color: '#1677ff' }} />
          <span>
            {score.name} ({score.code}) — 综合{' '}
            <Text style={{ ...NUM_FONT, color: getScoreColor(score.totalScore), fontSize: 20 }}>
              {score.totalScore}
            </Text>{' '}
            分
          </span>
        </Space>
      }
      extra={
        <Button icon={<CloseOutlined />} size="small" onClick={onClose} type="text">
          关闭
        </Button>
      }
    >
      <Row gutter={[24, 16]} justify="center">
        {DIMENSIONS.map((dim, i) => (
          <Col xs={12} sm={6} key={dim.key} style={{ textAlign: 'center' }}>
            <Progress
              type="dashboard"
              percent={values[i]}
              format={() => (
                <span style={{ ...NUM_FONT, fontSize: 18, color: dim.color }}>{values[i]}</span>
              )}
              strokeColor={dim.color}
              size={110}
              trailColor="#f0f0f0"
            />
            <div style={{ marginTop: 8, fontSize: 13, color: '#666', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 4 }}>
              {dim.icon}
              {dim.label}
            </div>
          </Col>
        ))}
      </Row>
    </Card>
  )
}

export default function StockScores() {
  const [data, setData] = useState<StockScore[]>([])
  const [selected, setSelected] = useState<StockScore | null>(null)
  const [date, setDate] = useState<dayjs.Dayjs | null>(null)
  const [minScore, setMinScore] = useState<number>(60)
  const [searchCode, setSearchCode] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = () => {
    setLoading(true)
    setError('')
    getScoreList(date?.format('YYYY-MM-DD') || undefined, minScore)
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const searchStock = () => {
    if (!searchCode.trim()) return
    setError('')
    getStockScore(searchCode.trim(), date?.format('YYYY-MM-DD') || undefined)
      .then(s => setSelected(s))
      .catch(e => setError(e.message))
  }

  const columns: ColumnsType<StockScore> = [
    {
      title: '代码',
      dataIndex: 'code',
      render: (code: string) => (
        <Space>
          <FundOutlined style={{ color: '#1677ff', fontSize: 14 }} />
          <Text style={{ ...NUM_FONT, fontSize: 14 }}>{code}</Text>
        </Space>
      ),
    },
    {
      title: '名称',
      dataIndex: 'name',
      render: (name: string) => name || '-',
    },
    {
      title: '总分',
      dataIndex: 'totalScore',
      width: 80,
      render: (score: number) => (
        <Text style={{ ...NUM_FONT, fontSize: 20, color: getScoreColor(score) }}>{score}</Text>
      ),
    },
    {
      title: '评分分布',
      width: 140,
      render: (_: unknown, record: StockScore) => <ScoreBar score={record} />,
    },
    ...DIMENSIONS.map(dim => ({
      title: (
        <Space size={4}>
          <span style={{ color: dim.color }}>{dim.icon}</span>
          <span>{dim.label}</span>
        </Space>
      ),
      dataIndex: `${dim.key}Score` as keyof StockScore,
      width: 80,
      render: (v: number) => (
        <Tag color={dim.color} style={{ ...NUM_FONT, margin: 0, borderRadius: 6 }}>{v}</Tag>
      ),
    })),
  ]

  return (
    <div className="page-container">
      {/* 页面标题 */}
      <div style={{ marginBottom: 24 }}>
        <Space align="center" size={12}>
          <div style={{
            width: 40, height: 40, borderRadius: 10,
            background: 'linear-gradient(135deg, #722ed1, #9254de)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <BarChartOutlined style={{ fontSize: 20, color: '#fff' }} />
          </div>
          <div>
            <Title level={4} style={{ margin: 0 }}>股票评分</Title>
            <Text type="secondary" style={{ fontSize: 13 }}>
              四层过滤模型：基本面 + 技术面 + 资金面 + 行业面
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
        <Select
          value={minScore}
          onChange={v => setMinScore(v)}
          style={{ width: 120 }}
          options={[
            { value: 0, label: '全部评分' },
            { value: 60, label: '>= 60分' },
            { value: 70, label: '>= 70分' },
            { value: 80, label: '>= 80分' },
          ]}
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={load}>
          查询
        </Button>
        <Input.Search
          placeholder="输入股票代码"
          value={searchCode}
          onChange={e => setSearchCode(e.target.value)}
          onSearch={searchStock}
          style={{ width: 200 }}
          enterButton
        />
        <Tooltip title="点击表格行查看评分详情">
          <InfoCircleOutlined style={{ color: '#999' }} />
        </Tooltip>
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

      {selected && <ScoreDetailCard score={selected} onClose={() => setSelected(null)} />}

      <div className="table-card">
        <Card
          title={
            <Space size={8}>
              <span style={{ fontWeight: 600 }}>评分列表</span>
              <Tag color="blue" style={{ borderRadius: 6 }}>{data.length} 只</Tag>
            </Space>
          }
          extra={
            <Space size={12} style={{ fontSize: 12 }}>
              {DIMENSIONS.map(dim => (
                <Text type="secondary" key={dim.key}>
                  <span style={{ color: dim.color, marginRight: 2 }}>{dim.icon}</span>
                  {dim.label}
                </Text>
              ))}
            </Space>
          }
        >
          <Spin spinning={loading}>
            {data.length === 0 && !loading ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  <Space direction="vertical" size={4}>
                    <Text type="secondary">暂无评分数据</Text>
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      请先运行评分任务，或尝试降低最低评分筛选条件
                    </Text>
                  </Space>
                }
              />
            ) : (
              <Table<StockScore>
                dataSource={data}
                columns={columns}
                rowKey="code"
                pagination={false}
                size="middle"
                onRow={record => ({
                  onClick: () => setSelected(record),
                  style: { cursor: 'pointer' },
                })}
              />
            )}
          </Spin>
        </Card>
      </div>
    </div>
  )
}
