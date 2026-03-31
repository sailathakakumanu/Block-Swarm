import React, { useState, useEffect } from 'react';

const API_BASE = 'http://localhost:8080/api/files';

function AllFiles() {
    const [files, setFiles] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [downloading, setDownloading] = useState(null);

    const fetchAllFiles = async () => {
        setLoading(true);
        setError(null);
        try {
            const res = await fetch(`${API_BASE}/all-files`, {
                credentials: 'include',
            });
            const data = await res.json();
            if (!res.ok) throw new Error(data.message || 'Failed to load files');
            setFiles(data.files || []);
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchAllFiles();
    }, []);

    const handleDownload = async (file) => {
        setDownloading(file.id);
        try {
            const res = await fetch(`${API_BASE}/download-by-id/${file.id}`, {
                credentials: 'include',
            });

            if (!res.ok) {
                const data = await res.json().catch(() => ({}));
                throw new Error(data.message || data.error || `HTTP ${res.status}`);
            }

            const blob = await res.blob();
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = file.fileName || 'download';
            a.click();
            URL.revokeObjectURL(url);
        } catch (err) {
            alert('Download failed: ' + err.message);
        } finally {
            setDownloading(null);
        }
    };

    const formatDate = (dateStr) => {
        if (!dateStr) return '—';
        const d = new Date(dateStr);
        return d.toLocaleDateString('en-US', {
            year: 'numeric', month: 'short', day: 'numeric',
            hour: '2-digit', minute: '2-digit',
        });
    };

    return (
        <div className="card position-relative">
            {downloading && (
                <div className="download-dialogue-overlay">
                    <div className="download-dialogue-card">
                        <span className="spinner lg-spinner" />
                        <h3 className="dialogue-title">Reconstructing File...</h3>
                        <p className="dialogue-text">Recovering fragments from Swarm nodes and decrypting...</p>
                    </div>
                </div>
            )}
            <div className="myfiles-header">
                <h2 className="card-title">
                    <span className="icon">🛡️</span> All Files (Admin)
                </h2>
                <button className="btn btn-refresh" onClick={fetchAllFiles} disabled={loading}>
                    🔄 Refresh
                </button>
            </div>

            {loading && (
                <div className="myfiles-loading">
                    <span className="spinner" /> Loading all files…
                </div>
            )}

            {error && (
                <div className="alert alert-error">
                    <strong>❌</strong> {error}
                </div>
            )}

            {!loading && !error && files.length === 0 && (
                <div className="myfiles-empty">
                    <div className="myfiles-empty-icon">📂</div>
                    <p>No files in the system yet.</p>
                </div>
            )}

            {!loading && files.length > 0 && (
                <div className="myfiles-list">
                    {files.map((file) => (
                        <div key={file.id} className="myfile-item">
                            <div className="myfile-info">
                                <div className="myfile-name">📄 {file.fileName}</div>
                                <div className="myfile-meta">
                                    <span className="myfile-owner">
                                        👤 {file.uploadedBy}
                                    </span>
                                    <span className="myfile-hash" title={file.fileHash}>
                                        🔑 {file.fileHash.substring(0, 16)}…
                                    </span>
                                    <span className="myfile-date">
                                        🕐 {formatDate(file.uploadTime)}
                                    </span>
                                </div>
                            </div>
                            <button
                                className="btn btn-download-sm"
                                onClick={() => handleDownload(file)}
                                disabled={downloading === file.id}
                            >
                                {downloading === file.id ? (
                                    <><span className="spinner" /> Downloading…</>
                                ) : (
                                    '⬇ Download'
                                )}
                            </button>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

export default AllFiles;
