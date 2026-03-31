import React, { useState } from 'react';

const API_BASE = 'http://localhost:8080/api/files';

function Download() {
    const [fileHash, setFileHash] = useState('');
    const [callerAddr, setCallerAddr] = useState('');
    const [loading, setLoading] = useState(false);
    const [servedFrom, setServedFrom] = useState(null);
    const [txInfo, setTxInfo] = useState(null);
    const [accessError, setAccessError] = useState(null);
    const [error, setError] = useState(null);

    const handleDownload = async () => {
        if (!fileHash.trim()) return;
        setLoading(true);
        setServedFrom(null);
        setTxInfo(null);
        setAccessError(null);
        setError(null);

        try {
            const headers = {};
            if (callerAddr.trim()) {
                headers['X-Caller-Address'] = callerAddr.trim();
            }

            const res = await fetch(`${API_BASE}/download/${fileHash.trim()}`, { 
                headers,
                credentials: 'include'
            });

            if (res.status === 403) {
                const data = await res.json();
                setAccessError(data.error || 'Access Denied.');
                return;
            }

            if (res.status === 404) {
                setError('File not found on blockchain.');
                return;
            }

            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                throw new Error(data.error || `HTTP ${res.status}`);
            }

            setServedFrom(res.headers.get('X-Served-From'));
            const txHash = res.headers.get('X-Tx-Hash');
            const blockNum = res.headers.get('X-Block-Number');
            if (txHash) setTxInfo({ txHash, blockNumber: blockNum });

            // Trigger browser file download
            const blob = await res.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            const cd = res.headers.get('Content-Disposition') || '';
            const match = cd.match(/filename="?([^"]+)"?/);
            a.download = match ? match[1] : 'recovered_file';
            a.href = url;
            a.click();
            URL.revokeObjectURL(url);

        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="card">
            <h2 className="card-title">
                <span className="icon">⬇️</span> Download File
            </h2>

            <div className="form-group">
                <label className="form-label">File Hash (SHA-256)</label>
                <input
                    className="form-input"
                    type="text"
                    placeholder="e.g. a3f5d9c…"
                    value={fileHash}
                    onChange={(e) => setFileHash(e.target.value)}
                />
            </div>

            <div className="form-group">
                <label className="form-label">Caller Address <span className="optional">(optional — defaults to server wallet)</span></label>
                <input
                    className="form-input"
                    type="text"
                    placeholder="0x…"
                    value={callerAddr}
                    onChange={(e) => setCallerAddr(e.target.value)}
                />
            </div>

            <button
                className="btn btn-secondary"
                onClick={handleDownload}
                disabled={!fileHash.trim() || loading}
            >
                {loading ? <span className="spinner" /> : null}
                {loading ? ' Downloading…' : '⬇ Download from Swarm'}
            </button>

            {accessError && (
                <div className="alert alert-denied">
                    <div className="denied-icon">🔒</div>
                    <div>
                        <strong>Access Denied (403)</strong>
                        <p>{accessError}</p>
                    </div>
                </div>
            )}

            {error && (
                <div className="alert alert-error">
                    <strong>❌ Error:</strong> {error}
                </div>
            )}

            {servedFrom && (
                <div className="result-panel">
                    <div className="served-from-row">
                        {servedFrom === 'EDGE_CACHE' ? (
                            <div className="served-badge edge-cache">
                                <span className="served-icon">⚡</span>
                                <div>
                                    <strong>Served from Edge Cache</strong>
                                    <p>File retrieved instantly from edge node — no swarm reconstruction needed.</p>
                                </div>
                            </div>
                        ) : (
                            <div className="served-badge swarm">
                                <span className="served-icon">🕸</span>
                                <div>
                                    <strong>Swarm Reconstructed</strong>
                                    <p>File rebuilt from distributed fragments across storage nodes. Now cached at edge.</p>
                                </div>
                            </div>
                        )}
                    </div>

                    {txInfo && (
                        <div className="info-grid" style={{ marginTop: '1rem' }}>
                            <div className="info-item">
                                <span className="info-label">📡 FileAccessed TX</span>
                                <span className="info-value mono">{txInfo.txHash}</span>
                            </div>
                            <div className="info-item">
                                <span className="info-label">⛓ Block Number</span>
                                <span className="info-value">{txInfo.blockNumber ? `#${txInfo.blockNumber}` : '—'}</span>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

export default Download;
