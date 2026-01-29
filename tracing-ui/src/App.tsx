import { Routes, Route, Link } from 'react-router-dom'
import { RecordListPage, RecordGraphPage, UploadPage } from './features/provenance'

export default function App() {
  return (
    <div className="app">
      <header className="header">
        <nav>
          <Link to="/" className="logo">Trace4EO</Link>
          <Link to="/upload" className="nav-link">Upload</Link>
        </nav>
      </header>
      <main className="main">
        <Routes>
          <Route path="/" element={<RecordListPage />} />
          <Route path="/upload" element={<UploadPage />} />
          <Route path="/records/:id/graph" element={<RecordGraphPage />} />
        </Routes>
      </main>
    </div>
  )
}
