import { BrowserRouter, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import RecommendPool from './pages/RecommendPool'
import StockScores from './pages/StockScores'
import IndustryRankPage from './pages/IndustryRankPage'
import SignalsPage from './pages/SignalsPage'
import LogViewerPage from './pages/LogViewerPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/recommend" element={<RecommendPool />} />
          <Route path="/scores" element={<StockScores />} />
          <Route path="/industry" element={<IndustryRankPage />} />
          <Route path="/signals" element={<SignalsPage />} />
          <Route path="/logs" element={<LogViewerPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
