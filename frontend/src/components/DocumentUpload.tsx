import React, { useState, useRef, useCallback } from 'react';
import { uploadDocument } from '../api/document';

interface DocumentUploadProps {
  isOpen: boolean;
  onClose: () => void;
}

const DOC_TYPES = ['项目资料', '开发规范', '新人培训', '问题排查', '技术文档', '其他'];

const DocumentUpload: React.FC<DocumentUploadProps> = ({ isOpen, onClose }) => {
  const [file, setFile] = useState<File | null>(null);
  const [docType, setDocType] = useState(DOC_TYPES[0]);
  const [uploading, setUploading] = useState(false);
  const [result, setResult] = useState<{ success: boolean; message: string } | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileSelect = useCallback((selectedFile: File) => {
    const validExts = ['.pdf', '.doc', '.docx', '.md', '.txt', '.html'];
    const ext = selectedFile.name.substring(selectedFile.name.lastIndexOf('.')).toLowerCase();
    if (!validExts.includes(ext)) {
      setResult({ success: false, message: `不支持的文件格式: ${ext}，请上传 PDF/Word/MD/TXT 文件` });
      return;
    }
    setFile(selectedFile);
    setResult(null);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragOver(false);
      const droppedFile = e.dataTransfer.files[0];
      if (droppedFile) handleFileSelect(droppedFile);
    },
    [handleFileSelect]
  );

  const handleUpload = useCallback(async () => {
    if (!file) return;
    setUploading(true);
    setResult(null);
    try {
      const res = await uploadDocument(file, docType);
      setResult(res);
      if (res.success) {
        setFile(null);
      }
    } finally {
      setUploading(false);
    }
  }, [file, docType]);

  const handleClose = useCallback(() => {
    setFile(null);
    setResult(null);
    setUploading(false);
    onClose();
  }, [onClose]);

  if (!isOpen) return null;

  return (
    <div className="modal-overlay" onClick={handleClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2 className="modal-title">📄 上传知识文档</h2>
          <button className="modal-close" onClick={handleClose}>
            ✕
          </button>
        </div>

        {/* Upload Zone */}
        {!file && (
          <div
            className={`upload-zone ${dragOver ? 'drag-over' : ''}`}
            onClick={() => fileInputRef.current?.click()}
            onDragOver={(e) => {
              e.preventDefault();
              setDragOver(true);
            }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleDrop}
          >
            <div className="upload-zone-icon">📂</div>
            <div className="upload-zone-text">点击或拖拽文件到此处上传</div>
            <div className="upload-zone-hint">支持 PDF、Word、Markdown、TXT 格式，最大 50MB</div>
          </div>
        )}

        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.doc,.docx,.md,.txt,.html"
          style={{ display: 'none' }}
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) handleFileSelect(f);
            e.target.value = '';
          }}
        />

        {/* Selected file info */}
        {file && (
          <div className="upload-file-info">
            <span className="upload-file-icon">📄</span>
            <span className="upload-file-name">{file.name}</span>
            <button className="upload-file-remove" onClick={() => setFile(null)}>
              ✕
            </button>
          </div>
        )}

        {/* Document type */}
        <select
          className="doc-type-select"
          value={docType}
          onChange={(e) => setDocType(e.target.value)}
        >
          {DOC_TYPES.map((t) => (
            <option key={t} value={t}>
              文档类型：{t}
            </option>
          ))}
        </select>

        {/* Upload button */}
        <button
          className="upload-submit-btn"
          onClick={handleUpload}
          disabled={!file || uploading}
        >
          {uploading ? '⏳ 正在处理文档...' : '🚀 上传并入库'}
        </button>

        {/* Result */}
        {result && (
          <div className={`upload-result ${result.success ? 'success' : 'error'}`}>
            {result.message}
          </div>
        )}
      </div>
    </div>
  );
};

export default DocumentUpload;
