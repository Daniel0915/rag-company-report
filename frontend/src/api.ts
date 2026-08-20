export interface DomainOption {
  value: string;
  label: string;
}

export interface MetadataOptions {
  docTypes: string[];
  domains: DomainOption[];
}

export interface UploadResult {
  status: string;
  filename: string;
  chunks: number;
}

export interface SourceItem {
  sourceFile: string;
  docType: string | null;
  chunkStrategy: string | null;
}

export interface ChatResponse {
  answer: string;
  sources: SourceItem[];
}

export interface ChatFilter {
  doc_type: string;
  domain: string;
}

const API = "/api/isms-p";

async function postJSON<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || "요청 실패");
  return data as T;
}

export async function fetchMetadataOptions(): Promise<MetadataOptions> {
  const res = await fetch(`${API}/metadata-options`);
  return res.json();
}

export async function uploadDocument(
  file: File,
  docType: string,
  domain: string,
  year: string,
): Promise<UploadResult> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("doc_type", docType);
  formData.append("domain", domain);
  formData.append("year", year);
  const res = await fetch(`${API}/upload`, { method: "POST", body: formData });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || "업로드 실패");
  return data as UploadResult;
}

export function deleteAllDocuments(): Promise<{ ok: boolean }> {
  return postJSON(`${API}/delete-all`, {});
}

export function sendChatMessage(question: string, filter: ChatFilter): Promise<ChatResponse> {
  return postJSON(`${API}/chat`, { question, filter });
}
