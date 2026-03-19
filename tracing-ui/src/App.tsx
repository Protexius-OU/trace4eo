import { useEffect, useCallback } from 'react'
import { Routes, Route, Link } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import './App.css'
import { setUserGetter } from './core/auth/authFetch'
import { RecordListPage, RecordGraphPage } from './features/provenance'

export default function App() {
  const auth = useAuth()

  const getUserFromAuth = useCallback(() => auth.user ?? null, [auth.user])

  useEffect(() => {
    setUserGetter(getUserFromAuth)
  }, [getUserFromAuth])

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
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10" />
                <path d="M2 12h20" />
                <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
              </svg>
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
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                <polyline points="16 17 21 12 16 7" />
                <line x1="21" y1="12" x2="9" y2="12" />
              </svg>
            </button>
          </div>
        </nav>
      </header>
      <main className="main">
        <Routes>
          <Route path="/" element={<RecordListPage />} />
          <Route path="/records/:id/graph" element={<RecordGraphPage />} />
        </Routes>
      </main>
    </div>
  )
}
