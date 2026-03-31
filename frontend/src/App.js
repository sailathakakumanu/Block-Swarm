import React, { useState, useEffect } from 'react';
import './App.css';
import Login from './components/Login';
import Upload from './components/Upload';
import Download from './components/Download';
import MyFiles from './components/MyFiles';
import AllFiles from './components/AllFiles';
import PublicFiles from './components/PublicFiles';
import SystemStats from './components/SystemStats';

const AUTH_API = 'http://localhost:8080/api/auth';

function App() {
  const [user, setUser] = useState(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [tab, setTab] = useState('upload');

  // Check if user is already logged in (session cookie)
  useEffect(() => {
    fetch(`${AUTH_API}/me`, { credentials: 'include' })
      .then((res) => (res.ok ? res.json() : null))
      .then((data) => {
        if (data && data.status === 'SUCCESS') {
          setUser({ userId: data.userId, username: data.username, role: data.role });
        }
      })
      .catch(() => { })
      .finally(() => setAuthChecked(true));
  }, []);

  const handleLogin = (userData) => {
    // userData includes { userId, username, role }
    setUser(userData);
    setTab('upload');
  };

  const handleLogout = async () => {
    await fetch(`${AUTH_API}/logout`, {
      method: 'POST',
      credentials: 'include',
    }).catch(() => { });
    setUser(null);
    setTab('upload');
  };

  // Show loading while checking session
  if (!authChecked) {
    return (
      <div className="app-root">
        <div className="auth-loading">
          <span className="spinner" /> Checking session…
        </div>
      </div>
    );
  }

  // Show login page if not authenticated
  if (!user) {
    return (
      <div className="app-root">
        <header className="app-header">
          <div className="header-inner">
            <div className="logo">
              <span className="logo-icon">🔗</span>
              <div>
                <h1 className="logo-title">BlockSwarm</h1>
                <p className="logo-sub">Blockchain-Based Encrypted Distributed Storage</p>
              </div>
            </div>
            <div className="header-pills">
              <span className="pill">⛓ Ganache</span>
              <span className="pill">🔐 ChaCha20-Poly1305 + ECC</span>
              <span className="pill">🕸 Swarm Storage</span>
            </div>
          </div>
        </header>
        <main className="main-content">
          <Login onLogin={handleLogin} />
        </main>
        <footer className="app-footer">
          BlockSwarm © 2026 · Ganache Local Blockchain ·
          SHA-256 Dedup · ChaCha20-Poly1305 + ECC Hybrid Encryption · XOR Parity Recovery
        </footer>
      </div>
    );
  }

  return (
    <div className="app-root">
      {/* Header */}
      <header className="app-header">
        <div className="header-inner">
          <div className="logo">
            <span className="logo-icon">🔗</span>
            <div>
              <h1 className="logo-title">BlockSwarm</h1>
              <p className="logo-sub">Blockchain-Based Encrypted Distributed Storage</p>
            </div>
          </div>
          <div className="header-right">
            <div className="header-pills">
              <span className="pill">⛓ Ganache</span>
              <span className="pill">🔐 ChaCha20-Poly1305 + ECC</span>
              <span className="pill">🕸 Swarm Storage</span>
            </div>
            <div className="user-bar">
              <span className="user-badge">
                {user.role === 'ADMIN' ? '🛡️' : '👤'} {user.username}
                {user.role === 'ADMIN' && <span className="admin-tag">ADMIN</span>}
              </span>
              <button className="btn-logout" onClick={handleLogout}>
                Logout
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Tab bar */}
      <nav className="tab-bar">
        <button
          className={`tab-btn ${tab === 'upload' ? 'active' : ''}`}
          onClick={() => setTab('upload')}
        >
          ☁️ Upload
        </button>
        <button
          className={`tab-btn ${tab === 'myfiles' ? 'active' : ''}`}
          onClick={() => setTab('myfiles')}
        >
          📁 My Files
        </button>
        <button
          className={`tab-btn ${tab === 'publicfiles' ? 'active' : ''}`}
          onClick={() => setTab('publicfiles')}
        >
          🌐 Public Files
        </button>
        {user.role === 'ADMIN' && (
          <button
            className={`tab-btn ${tab === 'allfiles' ? 'active' : ''}`}
            onClick={() => setTab('allfiles')}
          >
            🛡️ All Files
          </button>
        )}
        <button
          className={`tab-btn ${tab === 'download' ? 'active' : ''}`}
          onClick={() => setTab('download')}
        >
          ⬇️ Download
        </button>
        <button
          className={`tab-btn ${tab === 'stats' ? 'active' : ''}`}
          onClick={() => setTab('stats')}
        >
          📊 Statistics
        </button>
      </nav>

      {/* Content */}
      <main className="main-content">
        <div className="panel-container">
          {tab === 'upload' && <Upload />}
          {tab === 'myfiles' && <MyFiles />}
          {tab === 'publicfiles' && <PublicFiles />}
          {tab === 'allfiles' && user.role === 'ADMIN' && <AllFiles />}
          {tab === 'download' && <Download />}
          {tab === 'stats' && <SystemStats />}
        </div>

        {/* Architecture diagram row */}
        <div className="arch-row">
          <div className="arch-card">
            <div className="arch-icon">🔑</div>
            <div className="arch-label">Smart Deduplication</div>
            <div className="arch-desc">SHA-256 content hash prevents duplicate storage</div>
          </div>
          <div className="arch-arrow">→</div>
          <div className="arch-card">
            <div className="arch-icon">🔐</div>
            <div className="arch-label">ChaCha20-Poly1305 + ECC</div>
            <div className="arch-desc">Authenticated encryption with per-file key</div>
          </div>
          <div className="arch-arrow">→</div>
          <div className="arch-card">
            <div className="arch-icon">🕸</div>
            <div className="arch-label">Swarm Fragments</div>
            <div className="arch-desc">3 nodes + XOR parity for fault tolerance</div>
          </div>
          <div className="arch-arrow">→</div>
          <div className="arch-card">
            <div className="arch-icon">⚡</div>
            <div className="arch-label">Edge Cache</div>
            <div className="arch-desc">Instant retrieval from edge node</div>
          </div>
          <div className="arch-arrow">→</div>
          <div className="arch-card">
            <div className="arch-icon">⛓</div>
            <div className="arch-label">Blockchain ACL</div>
            <div className="arch-desc">Smart contract enforces ownership</div>
          </div>
        </div>
      </main>

      <footer className="app-footer">
        BlockSwarm © 2026 · Ganache Local Blockchain ·
        SHA-256 Dedup · ChaCha20-Poly1305 Encryption · XOR Parity Recovery
      </footer>
    </div>
  );
}

export default App;
