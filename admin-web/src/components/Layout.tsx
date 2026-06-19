import { Layout, Menu, Input, Badge, Space, Typography, Breadcrumb, theme, Avatar, Dropdown, Tooltip } from 'antd'
import {
  DashboardOutlined,
  StarOutlined,
  BarChartOutlined,
  AppstoreOutlined,
  AlertOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SearchOutlined,
  BulbFilled,
  BellOutlined,
  SettingOutlined,
  UserOutlined,
  ReloadOutlined,
  GithubOutlined,
  FundOutlined,
  LineChartOutlined,
  FileTextOutlined,
} from '@ant-design/icons'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useState } from 'react'
import './Layout.css'

const { Header, Sider, Content, Footer } = Layout
const { Title, Text } = Typography

/* 路由 -> 面包屑映射 */
const breadcrumbMap: Record<string, string> = {
  '/': '仪表盘',
  '/recommend': '推荐池',
  '/scores': '股票评分',
  '/industry': '行业排行',
  '/signals': '交易信号',
  '/logs': '系统日志',
}

/* 侧边栏菜单项 */
const menuItems = [
  {
    key: '/',
    icon: <DashboardOutlined />,
    label: <NavLink to="/">仪表盘</NavLink>,
  },
  {
    key: '/recommend',
    icon: <StarOutlined />,
    label: <NavLink to="/recommend">推荐池</NavLink>,
  },
  {
    key: '/scores',
    icon: <BarChartOutlined />,
    label: <NavLink to="/scores">股票评分</NavLink>,
  },
  {
    key: '/industry',
    icon: <AppstoreOutlined />,
    label: <NavLink to="/industry">行业排行</NavLink>,
  },
  {
    key: '/signals',
    icon: <AlertOutlined />,
    label: <NavLink to="/signals">交易信号</NavLink>,
  },
  {
    key: '/logs',
    icon: <FileTextOutlined />,
    label: <NavLink to="/logs">系统日志</NavLink>,
  },
]

/* 用户下拉菜单 */
const userMenuItems = [
  { key: 'profile', icon: <UserOutlined />, label: '个人设置' },
  { key: 'settings', icon: <SettingOutlined />, label: '系统配置' },
  { type: 'divider' as const },
  { key: 'github', icon: <GithubOutlined />, label: 'GitHub' },
]

export default function AppLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const location = useLocation()
  const { token } = theme.useToken()

  /* 当前路径对应的面包屑 */
  const breadcrumbItems = [
    { title: <NavLink to="/">首页</NavLink> },
    ...(breadcrumbMap[location.pathname]
      ? [{ title: breadcrumbMap[location.pathname] }]
      : []),
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* ===== 侧边栏 ===== */}
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        trigger={null}
        width={240}
        collapsedWidth={72}
        className="light-sider"
        style={{
          background: '#f8fafc',
          borderRight: '1px solid #e2e8f0',
          overflow: 'auto',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 100,
        }}
      >
        {/* Logo 区域 */}
        <div className="sider-logo">
          <div className="sider-logo-icon">
            <BulbFilled style={{ fontSize: 22, color: '#4f46e5' }} />
          </div>
          {!collapsed && (
            <div className="sider-logo-text">
              <span className="sider-logo-title">AI量化选股</span>
              <span className="sider-logo-sub">Quant Screening</span>
            </div>
          )}
        </div>

        {/* 导航菜单 */}
        <Menu
          theme="light"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          style={{
            background: 'transparent',
            borderRight: 0,
            padding: '8px 0',
          }}
        />

        {/* 底部信息 */}
        {!collapsed && (
          <div className="sider-footer">
            <div className="sider-footer-item">
              <FundOutlined style={{ fontSize: 12, color: '#22c55e' }} />
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>TickFlow 数据源</Text>
            </div>
            <div className="sider-footer-item">
              <LineChartOutlined style={{ fontSize: 12, color: '#6366f1' }} />
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>AKShare 备用</Text>
            </div>
          </div>
        )}
      </Sider>

      {/* ===== 右侧主区域 ===== */}
      <Layout style={{ marginLeft: collapsed ? 72 : 240, transition: 'margin-left 0.2s' }}>
        {/* 顶部 Header */}
        <Header
          className="app-header"
          style={{
            padding: '0 24px',
            background: token.colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
            height: 56,
            lineHeight: '56px',
            position: 'sticky',
            top: 0,
            zIndex: 99,
          }}
        >
          {/* 左侧：折叠按钮 + 面包屑 */}
          <Space size="middle" align="center">
            <span
              className="collapse-trigger"
              onClick={() => setCollapsed(!collapsed)}
            >
              {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            </span>
            <Breadcrumb items={breadcrumbItems} />
          </Space>

          {/* 右侧：搜索框 + 通知 + 用户 */}
          <Space size="middle" align="center">
            <Input
              prefix={<SearchOutlined style={{ color: token.colorTextQuaternary }} />}
              placeholder="搜索股票代码或名称..."
              allowClear
              style={{ width: 260, borderRadius: 8 }}
              className="header-search"
            />
            <Tooltip title="刷新数据">
              <span className="header-action">
                <ReloadOutlined />
              </span>
            </Tooltip>
            <Badge count={3} size="small" offset={[-2, 2]}>
              <span className="header-action">
                <BellOutlined />
              </span>
            </Badge>
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <Avatar
                size={32}
                icon={<UserOutlined />}
                style={{ backgroundColor: '#4f46e5', cursor: 'pointer' }}
              />
            </Dropdown>
          </Space>
        </Header>

        {/* 内容区 */}
        <Content style={{ padding: 24, background: token.colorBgLayout, minHeight: 280 }}>
          <Outlet />
        </Content>

        {/* Footer */}
        <Footer style={{ textAlign: 'center', color: token.colorTextSecondary, padding: '12px 24px' }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            AI量化选股系统 v1.0 · 数据仅供参考，不构成投资建议
          </Text>
        </Footer>
      </Layout>
    </Layout>
  )
}
