const API_BASE = 'http://localhost:8080/api';

export interface UploadResult {
  success: boolean;
  message: string;
}

/** 上传文档到知识库 */
export async function uploadDocument(
  file: File,
  docType: string
): Promise<UploadResult> {
  try {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('docType', docType);

    const res = await fetch(`${API_BASE}/document/upload`, {
      method: 'POST',
      body: formData,
    });

    const message = await res.text();
    return {
      success: message.includes('成功'),
      message,
    };
  } catch (err) {
    return {
      success: false,
      message: `上传失败：${(err as Error).message}`,
    };
  }
}
