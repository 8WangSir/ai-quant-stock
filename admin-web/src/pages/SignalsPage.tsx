import { useEffect, useState } from 'react'
import { getSellSignals } from '../api/client'
import type { TradeSignal } from '../types'
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
  Tooltip,
} from 'antd'
import {
  SearchOutlined,
  WarningOutlined,
  FallOutlined,
  LineChartOutlined,
  AppstoreOutlined,
  DollarOutlined,
  ExclamationCircleOutlined,
  FilterOutlined,
  CloseCircleOutlined,
  AlertFilled,
  SafetyCertificateOutlined,
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

function getSignalConfig(type: string) {
  const t = type.toLowerCase()
  if (t.includes('ma50') || t.includes('ma'))
    return { icon: <LineChartOutlined />, color: '#cf1322', bg: 'rgba(207, 19, 34, 0.08)', label: '均线' }
  if (t.includes('macd'))
    return { icon: <LineChartOutlined />, color: '#fa8c16', bg: 'rgba(250, 140, 22, 0.08)', label: 'MACD' }
  if (t.includes('industry') || t.includes('行业'))
    return { icon: <AppstoreOutlined />, color: '#1677ff', bg: 'rgba(22, 119, 255, 0.08)', label: '行业' }
  if (t.includes('capital') || t.includes('资金') || t.includes('主力'))
    return { icon: <DollarOutlined />, color: FALL_COLOR, bg: 'rgba(63, 134, 0, 0.08)', label: '资金' }
  if (t.includes('drawdown') || t.includes('回撤'))
    return { icon: <FallOutlined />, color: '#8b5cf6', bg: 'rgba(139, 92, 246, 0.08)', label: '回撤' }
  return { icon: <ExclamationCircleOutlined />, color: '#cf1322', bg: 'rgba(207, 19, 34, 0.08)', label: '其他' }
}

function getReasonStyle(type: string): React.CSSProperties {
  const config = getSignalConfig(type)
  return { borderLeft: `3px solid ${config.color}`, paddingLeft: 10 }
}

export default function SignalsPage() {
  const [data, setData] = useState<TradeSignal[]>([])
  const [date, setDate] = useState<dayjs.Dayjs | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = () => {
    setLoading(true)
    setError('')
    getSellSignals(date?.format('YYYY-MM-DD') || undefined)
      .then(setData)
      .catch(e => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, [])

  const columns: ColumnsType<TradeSignal> = [
    {
      title: '代码',
      dataIndex: 'code',
      render: (code: string) => (
        <Space>
          <AlertFilled style={{ color: '#cf1322', fontSize: 14 }} />
          <Text style={{ ...NUM_FONT, fontSize: 14 }}>{code}</Text>
        </Space>
      ),
    },
    {
      title: '名称',
      dataIndex: 'name',
      render: (name: string) => <Text strong>{name || '-'}</Text>,
    },
    {
      title: '信号类型',
      dataIndex: 'signalType',
      width: 180,
      render: (signalType: string) => {
        const config = getSignalConfig(signalType)
        return (
          <Space>
            <div style={{
              width: 28, height: 28, borderRadius: 6,
              background: config.bg,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              color: config.color, fontSize: 14,
            }}>
              {config.icon}
            </div>
            <div>
              <Tag color="red" style={{ fontWeight: 700, borderRadius: 6, margin: 0, display: 'block' }}>
                SELL
              </Tag>
              <Text type="secondary" style={{ fontSize: 11 }}>{signalType}</Text>
            </div>
          </Space>
        )
      },
    },
    {
      title: '原因说明',
      dataIndex: 'reason',
      render: (reason: string, record: TradeSignal) => (
        <div style={{
          ...getReasonStyle(record.signalType),
          padding: '8px 12px',
          background: '#fafafa',
          borderRadius: '0 6px 6px 0',
        }}>
          <Text type="secondary" style={{ fontSize: 13, lineHeight: 1.6 }}>{reason}</Text>
        </div>
      ),
    },
    {
      title: '日期',
      dataIndex: 'tradeDate',
      width: 120,
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
            background: 'linear-gradient(135deg, #cf1322, #ff4d4f)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <SafetyCertificateOutlined style={{ fontSize: 20, color: '#fff' }} />
          </div>
          <div>
            <Title level={4} style={{ margin: 0 }}>卖出信号</Title>
            <Text type="secondary" style={{ fontSize: 13 }}>
              跌破MA50 / MACD死叉 / 行业走弱 / 主力流出 / 回撤超8%
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
              <WarningOutlined style={{ color: '#cf1322' }} />
              <span style={{ fontWeight: 600 }}>卖出信号列表</span>
              <Tag color="red" style={{ borderRadius: 6 }}>
                <ExclamationCircleOutlined /> {data.length} 条
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
                    <Text type="secondary">暂无卖出信号</Text>
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      当前持仓股票暂未触发卖出预警信号，请持续关注
                    </Text>
                  </Space>
                }
              />
            ) : (
              <Table<TradeSignal>
                dataSource={data}
                columns={columns}
                rowKey={(record, index) => `${record.code}-${index}`}
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
