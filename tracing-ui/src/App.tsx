import { Routes, Route, Link } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { Globe, LogOut } from 'lucide-react'
import './App.css'
import { RecordListPage, RecordGraphPage } from './features/provenance'
import { LocationsMapPage } from './features/locations'

export default function App() {
  const auth = useAuth()

  if (auth.isLoading) {
    return <div className="app"><main className="main"><p>Loading...</p></main></div>
  }

  if (auth.error) {
    return (
      <div className="app">
        <main className="main">
          <p>Authentication error: {auth.error.message}</p>
          <button className="btn btn-primary" onClick={() => auth.signinRedirect()}>
            Try Again
          </button>
        </main>
      </div>
    )
  }

  if (!auth.isAuthenticated) {
    return (
      <div className="app">
        <main className="main login">
          <div className="login-card">
            <div className="login-icon">
              <Globe size={48} aria-hidden="true" />
            </div>
            <h1 className="login-title">Trace4EO</h1>
            <p className="login-subtitle">Earth Observation Provenance Tracing</p>
            <hr className="login-divider" />
            <button className="login-btn" onClick={() => auth.signinRedirect()}>
              Sign In
            </button>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div className="app">
      <header className="header">
        <nav>
          <Link to="/" className="logo">Trace4EO</Link>
          <div className="nav-right">
            <span className="nav-user">{auth.user?.profile.email}</span>
            <button className="btn-icon" onClick={() => auth.signoutRedirect()} title="Sign Out">
              <LogOut size={18} aria-hidden="true" />
            </button>
          </div>
        </nav>
      </header>
      <main className="main">
        <Routes>
          <Route path="/" element={<RecordListPage />} />
          <Route path="/records/:id/graph" element={<RecordGraphPage />} />
          <Route path="/map" element={<LocationsMapPage />} />
        </Routes>
      </main>
    </div>
  )
}
