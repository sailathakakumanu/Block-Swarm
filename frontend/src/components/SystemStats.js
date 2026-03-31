import React, { useState, useEffect, useCallback } from 'react';

const API_BASE = 'http://localhost:8080/api/stats';

const LAYERS = [
    { icon: '📤', name: 'Data Upload Layer', desc: 'Handles file upload, encryption, hashing and deduplication' },
    { icon: '⛓', name: 'Blockchain Layer', desc: 'Stores immutable metadata and ownership on-chain' },
    { icon: '💾', name: 'Storage Layer', desc: 'Distributes encrypted fragments across 3 nodes' },
    { icon: '🕸', name: 'Swarm Recovery Layer', desc: 'Reconstructs files with XOR parity fault tolerance' },
    { icon: '⚡', name: 'Edge Layer', desc: 'Caches reconstructed files for instant retrieval' },
    { icon: '📥', name: 'Data Retrieval Layer', desc: 'Handles ownership verification, decryption and delivery' },
];

function SystemStats() {
    const [nodes, setNodes] = useState(null);
    const [edge, setEdge] = useState(null);
    const [blockchain, setBlockchain] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const fetchStats = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const [nodesRes, edgeRes, bcRes] = await Promise.all([
                fetch(`${API_BASE}/nodes`),
                fetch(`${API_BASE}/edge`),
                fetch(`${API_BASE}/blockchain`),
            ]);
            setNodes(await nodesRes.json());
            setEdge(await edgeRes.json());
            setBlockchain(await bcRes.json());
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { fetchStats(); }, [fetchStats]);

    const formatBytes = (bytes) => {
        if (bytes < 0) return '—';
        if (bytes < 1024) return bytes + ' B';
        return (bytes / 1024).toFixed(1) + ' KB';
    };

    const truncate = (str, len = 18) =>
        str && str.length > len ? str.slice(0, len) + '…' : str;

    return (
        <div className="stats-page">
            {/* Header row */}
            <div className="stats-header-row">
                <h2 className="card-title" style={{ marginBottom: 0 }}>
                    <span className="icon">📊</span> System Statistics
                </h2>
                <button className="btn btn-refresh" onClick={fetchStats} disabled={loading}>
                    {loading ? <span className="spinner" /> : '🔄'} Refresh
                </button>
            </div>

            {error && (
                <div className="alert alert-error">
                    <strong>❌ Error:</strong> {error}
                    <p style={{ fontSize: '0.78rem', marginTop: '0.3rem' }}>
                        Make sure the backend is running on port 8080.
                    </p>
                </div>
            )}

            {/* ─── Storage Nodes ─────────────────────────────────── */}
            <h3 className="section-title">💾 Storage Nodes</h3>
            <div className="stats-grid-3">
                {['storage_node1', 'storage_node2', 'storage_node3'].map((nodeName) => {
                    const files = nodes?.[nodeName] || [];
                    return (
                        <div key={nodeName} className="stats-card">
                            <div className="stats-card-header">
                                <span className="stats-card-icon">📂</span>
                                <span className="stats-card-name">{nodeName}</span>
                            </div>
                            <div className="stats-card-count">{files.length} file{files.length !== 1 ? 's' : ''}</div>
                            {files.length > 0 ? (
                                <ul className="file-list">
                                    {files.map((f, i) => (
                                        <li key={i} className="file-list-item">
                                            <span className="file-list-name" title={f.name}>{truncate(f.name, 24)}</span>
                                            <span className="file-list-size">{formatBytes(f.size)}</span>
                                        </li>
                                    ))}
                                </ul>
                            ) : (
                                <p className="empty-msg">No files stored</p>
                            )}
                        </div>
                    );
                })}
            </div>

            {/* ─── Edge Cache ────────────────────────────────────── */}
            <h3 className="section-title">⚡ Edge Cache</h3>
            <div className="stats-card" style={{ marginBottom: '2rem' }}>
                <div className="stats-card-header">
                    <span className="stats-card-icon">🌐</span>
                    <span className="stats-card-name">edge_node</span>
                </div>
                <div className="stats-card-count">
                    {(edge?.edge_node || []).length} file{(edge?.edge_node || []).length !== 1 ? 's' : ''}
                </div>
                {(edge?.edge_node || []).length > 0 ? (
                    <ul className="file-list">
                        {edge.edge_node.map((f, i) => (
                            <li key={i} className="file-list-item">
                                <span className="file-list-name" title={f.name}>{truncate(f.name, 30)}</span>
                                <span className="file-list-size">{formatBytes(f.size)}</span>
                            </li>
                        ))}
                    </ul>
                ) : (
                    <p className="empty-msg">Cache is empty</p>
                )}
            </div>

            {/* ─── Blockchain Transactions ───────────────────────── */}
            <h3 className="section-title">⛓ Blockchain Information</h3>
            <div className="stats-card" style={{ marginBottom: '2rem' }}>
                {blockchain?.defaultAddress && (
                    <div className="bc-address">
                        <span className="info-label">🔑 Default Wallet</span>
                        <span className="info-value mono">{blockchain.defaultAddress}</span>
                    </div>
                )}

                {blockchain?.transactions?.length > 0 ? (
                    <div className="tx-table-wrap">
                        <table className="tx-table">
                            <thead>
                                <tr>
                                    <th>TX Hash</th>
                                    <th>Block</th>
                                    <th>From</th>
                                    <th>To</th>
                                </tr>
                            </thead>
                            <tbody>
                                {blockchain.transactions.map((tx, i) => (
                                    <tr key={i}>
                                        <td className="mono" title={tx.txHash}>{truncate(tx.txHash, 16)}</td>
                                        <td>#{tx.blockNumber}</td>
                                        <td className="mono" title={tx.from}>{truncate(tx.from, 12)}</td>
                                        <td className="mono" title={tx.to}>{truncate(tx.to, 12)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                ) : (
                    <p className="empty-msg">No transactions found. Upload a file to create blockchain records.</p>
                )}
            </div>

            {/* ─── System Layers Visualization ───────────────────── */}
            <h3 className="section-title">🏗 System Architecture Layers</h3>
            <div className="layer-pipeline">
                {LAYERS.map((layer, i) => (
                    <React.Fragment key={i}>
                        <div className="layer-card">
                            <span className="layer-icon">{layer.icon}</span>
                            <div className="layer-text">
                                <strong>{layer.name}</strong>
                                <span>{layer.desc}</span>
                            </div>
                        </div>
                        {i < LAYERS.length - 1 && <div className="layer-connector">▼</div>}
                    </React.Fragment>
                ))}
            </div>
        </div>
    );
}

export default SystemStats;
