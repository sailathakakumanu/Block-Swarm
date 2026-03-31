import React, { useState, useEffect } from 'react';

const API_BASE = 'http://localhost:8080/api/files';

function Upload() {
    const [file, setFile] = useState(null);
    const [loading, setLoading] = useState(false);
    const [result, setResult] = useState(null);
    const [error, setError] = useState(null);
    const [isPublic, setIsPublic] = useState(false);
    const [users, setUsers] = useState([]);
    const [sharedWith, setSharedWith] = useState([]);

    useEffect(() => {
        fetchUsers();
    }, []);

    const fetchUsers = async () => {
        try {
            const res = await fetch(`${API_BASE}/users/share-list`, { credentials: 'include' });
            if (res.ok) {
                const data = await res.json();
                setUsers(data);
            }
        } catch (err) {
            console.error('Failed to fetch user list:', err);
        }
    };

    const handleUpload = async () => {
        if (!file) return;
        setLoading(true);
        setResult(null);
        setError(null);

        try {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('isPublic', isPublic);
            if (!isPublic && sharedWith.length > 0) {
                sharedWith.forEach(id => formData.append('sharedWith', id));
            }

            const res = await fetch(`${API_BASE}/upload`, {
                method: 'POST',
                body: formData,
                credentials: 'include',
            });

            const data = await res.json();
            if (!res.ok) throw new Error(data.message || 'Upload failed');
            setResult(data);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const toggleSharedUser = (id) => {
        setSharedWith(prev => 
            prev.includes(id) ? prev.filter(uId => uId !== id) : [...prev, id]
        );
    };

    const statusColor = (s) => {
        if (s === 'SUCCESS') return '#22c55e';
        if (s === 'DUPLICATE') return '#f59e0b';
        return '#ef4444';
    };

    return (
        <div className="card">
            <h2 className="card-title">
                <span className="icon">☁️</span> Upload File
            </h2>

            <div className="drop-zone">
                <input
                    id="file-input"
                    type="file"
                    className="file-input-hidden"
                    onChange={(e) => { setFile(e.target.files[0]); setResult(null); setError(null); }}
                />
                <label htmlFor="file-input" className="drop-label">
                    {file ? (
                        <span className="file-chosen">📄 {file.name} <span className="file-size">({(file.size / 1024).toFixed(1)} KB)</span></span>
                    ) : (
                        <span>Click to choose a file…</span>
                    )}
                </label>
            </div>

            <div className="visibility-toggle" style={{ margin: '1rem 0', display: 'flex', flexDirection: 'column', gap: '0.8rem' }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', fontWeight: 'bold' }}>
                    <input 
                        type="checkbox" 
                        checked={isPublic} 
                        onChange={(e) => setIsPublic(e.target.checked)} 
                        style={{ width: '1.2rem', height: '1.2rem' }}
                    />
                    <span>Make this file Public (Visible to everyone)</span>
                </label>

                {!isPublic && users.length > 0 && (
                    <div className="share-section" style={{ padding: '1rem', background: 'rgba(255,255,255,0.05)', borderRadius: '8px' }}>
                        <span style={{ fontSize: '0.9rem', color: '#94a3b8', marginBottom: '0.5rem', display: 'block' }}>
                            👤 Grant Access to Registered Users (Hybrid Encryption)
                        </span>
                        <div className="user-selection-list" style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
                            {users.map(u => (
                                <button
                                    key={u.id}
                                    type="button"
                                    onClick={() => toggleSharedUser(u.id)}
                                    style={{
                                        padding: '5px 12px',
                                        borderRadius: '20px',
                                        border: '1px solid #475569',
                                        background: sharedWith.includes(u.id) ? '#3b82f6' : 'transparent',
                                        color: 'white',
                                        cursor: 'pointer',
                                        fontSize: '0.85rem',
                                        transition: 'all 0.2s'
                                    }}
                                >
                                    {sharedWith.includes(u.id) ? '✅ ' : '+ '} {u.username}
                                </button>
                            ))}
                        </div>
                    </div>
                )}
            </div>

            <button
                className="btn btn-primary"
                onClick={handleUpload}
                disabled={!file || loading}
            >
                {loading ? <span className="spinner" /> : null}
                {loading ? ' Uploading…' : '⬆ Upload to Blockchain'}
            </button>

            {error && (
                <div className="alert alert-error">
                    <strong>❌ Error:</strong> {error}
                </div>
            )}

            {result && (
                <div className="result-panel">
                    <div className="status-row">
                        <span className="status-badge" style={{ background: statusColor(result.status) }}>
                            {result.status === 'SUCCESS' && '✅ New File Stored'}
                            {result.status === 'DUPLICATE' && '⚠️ Duplicate Detected'}
                            {result.status === 'ERROR' && '❌ Error'}
                        </span>
                        {result.status === 'DUPLICATE' && (
                            <span className="dedup-note">You were added as an owner — no duplicate fragments created.</span>
                        )}
                    </div>

                    <div className="info-grid">
                        <div className="info-item">
                            <span className="info-label">📋 File Hash</span>
                            <span className="info-value mono">{result.fileHash}</span>
                        </div>
                        <div className="info-item">
                            <span className="info-label">🔗 TX Hash</span>
                            <span className="info-value mono">{result.txHash || '—'}</span>
                        </div>
                        <div className="info-item">
                            <span className="info-label">⛓ Block Number</span>
                            <span className="info-value">{result.blockNumber != null ? `#${result.blockNumber}` : '—'}</span>
                        </div>
                        {result.encryption && (
                            <div className="info-item">
                                <span className="info-label">🔐 Encryption</span>
                                <span className="info-value">{result.encryption}</span>
                            </div>
                        )}
                    </div>

                    {result.nodes && (
                        <div className="nodes-section">
                            <span className="info-label">📂 Storage Nodes</span>
                            <div className="nodes-grid">
                                {result.nodes.map((n) => (
                                    <span key={n} className="node-badge">✅ {n}</span>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

export default Upload;
