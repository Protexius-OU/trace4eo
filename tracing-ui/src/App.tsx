import { useEffect } from 'react'
import { Routes, Route, Link } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { setCurrentUser } from './core/auth/authFetch'
import { RecordListPage, RecordGraphPage, UploadPage } from './features/provenance'

export default function App() {
  const auth = useAuth()

  useEffect(() => {
    setCurrentUser(auth.user ?? null)
  }, [auth.user])

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
        <header className="header">
          <nav>
            <span className="logo">Trace4EO</span>
          </nav>
        </header>
        <main className="main">
          <h1>Welcome to Trace4EO</h1>
          <p>Please sign in to continue.</p>
          <button className="btn btn-primary" onClick={() => auth.signinRedirect()}>
            Sign In
          </button>
        </main>
      </div>
    )
  }

  return (
    <div className="app">
      <header className="header">
        <nav>
          <Link to="/" className="logo">Trace4EO</Link>
          <Link to="/upload" className="nav-link">Upload</Link>
          <span className="nav-user">{auth.user?.profile.email}</span>
          <button className="btn btn-secondary btn-sm" onClick={() => auth.signoutRedirect()}>
            Sign Out
          </button>
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
