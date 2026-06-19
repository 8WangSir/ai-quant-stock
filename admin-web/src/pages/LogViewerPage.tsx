import { useEffect, useRef, useState, useCallback } from 'react'
import {
  Card,
  Select,
  Tag,
  Space,
  Button,
  Typography,
  Alert,
  Badge,
  Tooltip,
  Divider,
  Flex,
} from 'antd'
import {
  FileTextOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ClearOutlined,
  SyncOutlined,
  BugOutlined,
  InfoCircleOutlined,
  WarningOutlined,
  CloseCircleOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons'

const { Text } = Typography

interface LogEntry {
  service: string
  level: string
  raw: string
  timestamp: number
}

const SERVICE_COLORS: Record<string, string> = {
  'admin-service': '#1677ff',
  'market-data-service': '#52c41a',
  'factor-engine-service': '#722ed1',
  'strategy-service': '#fa8c16',
  'notify-service': '#eb2f96',
}

const LEVEL_CONFIG: Record<string, { color: string; icon: React.ReactNode; bg: string }> = {
  ERROR: { color: '#cf1322', icon: <CloseCircleOutlined />, bg: '#fff1f0' },
  WARN: { color: '#d48806', icon: <WarningOutlined />, bg: '#fffbe6' },
  INFO: { color: '#1677ff', icon: <InfoCircleOutlined />, bg: '#e6f4ff' },
}

const NUM_FONT: React.CSSProperties = {
  fontFamily: "'BaiduNumberPlus', 'PingFang SC', 'Microsoft YaHei', -apple-system, sans-serif",
  fontVariantNumeric: 'tabular-nums',
  letterSpacing: '-0.02em',
}

export default function LogViewerPage() {
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [services, setServices] = useState<string[]>([])
  const [selectedService, setSelectedService] = useState<string>('')
  const [selectedLevel, setSelectedLevel] = useState<string>('')
  const [connected, setConnected] = useState(false)
  const [paused, setPaused] = useState(false)
  const [error, setError] = useState('')
  const logContainerRef = useRef<HTMLDivElement>(null)
  const eventSourceRef = useRef<EventSource | null>(null)
  const logsRef = useRef<LogEntry[]>([])

  const connect = useCallback(() => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
    }

    setError('')
    const params = new URLSearchParams()
    if (selectedService) params.append('service', selectedService)
    if (selectedLevel) params.append('level', selectedLevel)

    const url = `/api/logs/stream?${params.toString()}`
    const es = new EventSource(url)
    eventSourceRef.current = es

    es.onopen = () => {
      setConnected(true)
    }

    es.addEventListener('services', (e) => {
      try {
        const data = JSON.parse(e.data)
        setServices(data)
      } catch { /* ignore */ }
    })

    es.addEventListener('log', (e) => {
      try {
        const entry: LogEntry = JSON.parse(e.data)
        logsRef.current = [...logsRef.current, entry].slice(-2000) // 最多保留2000条
        if (!paused) {
          setLogs(logsRef.current)
        }
      } catch { /* ignore */ }
    })

    es.onerror = () => {
      setConnected(false)
      setError('日志连接断开，正在重连...')
      es.close()
      // 3秒后重连
      setTimeout(() => connect(), 3000)
    }
  }, [selectedService, selectedLevel, paused])

  useEffect(() => {
    connect()
    return () => {
      eventSourceRef.current?.close()
    }
  }, [connect])

  // 自动滚动到底部
  useEffect(() => {
    if (!paused && logContainerRef.current) {
      logContainerRef.current.scrollTop = logContainerRef.current.scrollHeight
    }
  }, [logs, paused])

  const clearLogs = () => {
    logsRef.current = []
    setLogs([])
  }

  const togglePause = () => {
    setPaused(!paused)
    if (paused) {
      setLogs(logsRef.current)
    }
  }

  const filteredLogs = logs.filter((log) => {
    if (selectedService && log.service !== selectedService) return false
    if (selectedLevel && log.level !== selectedLevel) return false
    return true
  })

  const stats = {
    total: logs.length,
    error: logs.filter((l) => l.level === 'ERROR').length,
    warn: logs.filter((l) => l.level === 'WARN').length,
    info: logs.filter((l) => l.level === 'INFO').length,
  }

  return (
    <div className="page-container">
      {/* 页面标题 */}
      <div style={{ marginBottom: 24 }}>
        <Space align="center" size={12}>
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              background: 'linear-gradient(135deg, #1677ff 0%, #0958d9 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <FileTextOutlined style={{ color: '#fff', fontSize: 20 }} />
          </div>
          <div>
            <h2 style={{ margin: 0, fontWeight: 700, fontSize: 20 }}>系统日志</h2>
            <Text type="secondary" style={{ fontSize: 13 }}>
              实时查看所有后端服务日志
            </Text>
          </div>
          <Badge
            status={connected ? 'processing' : 'error'}
            text={
              <Text type={connected ? 'success' : 'danger'} style={{ fontSize: 12 }}>
                {connected ? '实时连接中' : '连接断开'}
              </Text>
            }
          />
        </Space>
      </div>

      {error && (
        <Alert
          message={error}
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          closable
          onClose={() => setError('')}
        />
      )}

      {/* 统计栏 */}
      <Card size="small" style={{ marginBottom: 16, borderRadius: 10 }}>
        <Flex gap={24} align="center" wrap="wrap">
          <Space>
            <BugOutlined style={{ color: '#1677ff' }} />
            <Text>总日志</Text>
            <Text strong style={{ ...NUM_FONT, fontSize: 16, color: '#1677ff' }}>
              {stats.total}
            </Text>
          </Space>
          <Space>
            <CloseCircleOutlined style={{ color: '#cf1322' }} />
            <Text>错误</Text>
            <Text strong style={{ ...NUM_FONT, fontSize: 16, color: '#cf1322' }}>
              {stats.error}
            </Text>
          </Space>
          <Space>
            <WarningOutlined style={{ color: '#d48806' }} />
            <Text>警告</Text>
            <Text strong style={{ ...NUM_FONT, fontSize: 16, color: '#d48806' }}>
              {stats.warn}
            </Text>
          </Space>
          <Space>
            <InfoCircleOutlined style={{ color: '#1677ff' }} />
            <Text>信息</Text>
            <Text strong style={{ ...NUM_FONT, fontSize: 16, color: '#1677ff' }}>
              {stats.info}
            </Text>
          </Space>
          <div style={{ flex: 1 }} />
          <Space>
            <Button
              icon={paused ? <PlayCircleOutlined /> : <PauseCircleOutlined />}
              onClick={togglePause}
              type={paused ? 'primary' : 'default'}
            >
              {paused ? '继续' : '暂停'}
            </Button>
            <Button icon={<ClearOutlined />} onClick={clearLogs}>
              清空
            </Button>
            <Button icon={<SyncOutlined />} onClick={connect}>
              重连
            </Button>
          </Space>
        </Flex>
      </Card>

      {/* 筛选栏 */}
      <Card size="small" style={{ marginBottom: 16, borderRadius: 10 }}>
        <Flex gap={16} align="center" wrap="wrap">
          <Space>
            <Text strong>服务筛选:</Text>
            <Select
              style={{ width: 180 }}
              placeholder="全部服务"
              allowClear
              value={selectedService || undefined}
              onChange={(v) => setSelectedService(v || '')}
              options={services.map((s) => ({ label: s, value: s }))}
            />
          </Space>
          <Space>
            <Text strong>级别筛选:</Text>
            <Select
              style={{ width: 120 }}
              placeholder="全部级别"
              allowClear
              value={selectedLevel || undefined}
              onChange={(v) => setSelectedLevel(v || '')}
              options={[
                { label: 'INFO', value: 'INFO' },
                { label: 'WARN', value: 'WARN' },
                { label: 'ERROR', value: 'ERROR' },
              ]}
            />
          </Space>
          <div style={{ flex: 1 }} />
          {paused && (
            <Tag color="warning" icon={<PauseCircleOutlined />}>
              已暂停
            </Tag>
          )}
        </Flex>
      </Card>

      {/* 日志列表 */}
      <Card
        style={{ borderRadius: 10 }}
        bodyStyle={{ padding: 0 }}
        title={
          <Flex justify="space-between" align="center">
            <Space>
              <FileTextOutlined style={{ color: '#1677ff' }} />
              <span style={{ fontWeight: 600 }}>实时日志流</span>
              <Tag color="blue">{filteredLogs.length} 条</Tag>
            </Space>
            {connected && !paused && (
              <Badge status="processing" text="实时接收中" />
            )}
          </Flex>
        }
      >
        <div
          ref={logContainerRef}
          style={{
            height: 'calc(100vh - 420px)',
            minHeight: 400,
            overflowY: 'auto',
            overflowX: 'auto',
            fontFamily: "'SFMono-Regular', Consolas, 'Courier New', monospace",
            fontSize: 13,
            lineHeight: 1.6,
            background: '#fafafa',
            padding: '12px 16px',
          }}
        >
          {filteredLogs.length === 0 ? (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                color: '#999',
              }}
            >
              <Space direction="vertical" align="center">
                <FileTextOutlined style={{ fontSize: 48, color: '#d9d9d9' }} />
                <Text type="secondary">暂无日志</Text>
                {!connected && <Text type="danger">连接已断开</Text>}
              </Space>
            </div>
          ) : (
            filteredLogs.map((log, index) => {
              const levelCfg = LEVEL_CONFIG[log.level] || LEVEL_CONFIG.INFO
              const svcColor = SERVICE_COLORS[log.service] || '#999'
              return (
                <div
                  key={index}
                  style={{
                    padding: '4px 8px',
                    marginBottom: 2,
                    borderRadius: 4,
                    background: levelCfg.bg,
                    borderLeft: `3px solid ${levelCfg.color}`,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    whiteSpace: 'nowrap',
                  }}
                >
                  <Tag
                    color={svcColor}
                    style={{
                      fontSize: 11,
                      padding: '0 6px',
                      height: 20,
                      lineHeight: '20px',
                      flexShrink: 0,
                      margin: 0,
                    }}
                  >
                    {log.service}
                  </Tag>
                  <Tooltip title={log.level}>
                    <span style={{ color: levelCfg.color, flexShrink: 0 }}>
                      {levelCfg.icon}
                    </span>
                  </Tooltip>
                  <span style={{ color: '#333', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}>{log.raw}</span>
                </div>
              )
            })
          )}
          <div ref={(el) => {
            if (el && !paused) {
              el.scrollIntoView({ behavior: 'smooth' })
            }
          }} />
        </div>
      </Card>
    </div>
  )
}
