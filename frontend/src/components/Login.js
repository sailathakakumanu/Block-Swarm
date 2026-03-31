import React, { useState } from 'react';

const API_BASE = 'http://localhost:8080/api/auth';

function Login({ onLogin }) {
    const [isSignup, setIsSignup] = useState(false);
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [success, setSuccess] = useState(null);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        setSuccess(null);

        try {
            const endpoint = isSignup ? '/signup' : '/login';
            const res = await fetch(`${API_BASE}${endpoint}`, {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify({ username, password }),
            });

            const data = await res.json();

            if (!res.ok) {
                throw new Error(data.message || 'Authentication failed');
            }

            if (isSignup) {
                setSuccess('Account created! You can now log in.');
                setIsSignup(false);
                setPassword('');
            } else {
                onLogin({ userId: data.userId, username: data.username, role: data.role });
            }
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-container">
            <div className="auth-card">
                <div className="auth-icon">🔐</div>
                <h2 className="auth-title">
                    {isSignup ? 'Create Account' : 'Welcome Back'}
                </h2>
                <p className="auth-subtitle">
                    {isSignup
                        ? 'Sign up to start storing files on the blockchain'
                        : 'Log in to access your decentralized files'}
                </p>

                <form onSubmit={handleSubmit} className="auth-form">
                    <div className="form-group">
                        <label className="form-label">Username</label>
                        <input
                            className="form-input"
                            type="text"
                            placeholder="Enter username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label className="form-label">Password</label>
                        <input
                            className="form-input"
                            type="password"
                            placeholder="Enter password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                            minLength={4}
                        />
                    </div>

                    <button
                        type="submit"
                        className="btn btn-primary"
                        disabled={loading || !username || !password}
                    >
                        {loading ? <span className="spinner" /> : null}
                        {loading
                            ? (isSignup ? ' Creating Account…' : ' Logging in…')
                            : (isSignup ? '🚀 Sign Up' : '🔓 Log In')}
                    </button>
                </form>

                {error && (
                    <div className="alert alert-error">
                        <strong>❌</strong> {error}
                    </div>
                )}

                {success && (
                    <div className="alert alert-success">
                        <strong>✅</strong> {success}
                    </div>
                )}

                <div className="auth-toggle">
                    {isSignup ? (
                        <span>
                            Already have an account?{' '}
                            <button className="link-btn" onClick={() => { setIsSignup(false); setError(null); setSuccess(null); }}>
                                Log In
                            </button>
                        </span>
                    ) : (
                        <span>
                            Don't have an account?{' '}
                            <button className="link-btn" onClick={() => { setIsSignup(true); setError(null); setSuccess(null); }}>
                                Sign Up
                            </button>
                        </span>
                    )}
                </div>
            </div>
        </div>
    );
}

export default Login;
