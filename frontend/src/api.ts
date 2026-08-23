export interface WatchedCompany {
  name: string;
  corpCode: string;
  stockCode: string;
}

export interface IndexResult {
  corpName: string;
  rceptNo: string;
  reportNm: string;
  status: string;
  chunks: number;
}

export interface SourceItem {
  corpName: string;
  reportNm: string;
  rceptNo: string;
  sectionTitle: string;
}

export interface ChatResponse {
  answer: string;
  sources: SourceItem[];
}

const API = "/api/company-report";

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

export async function fetchWatchlist(): Promise<WatchedCompany[]> {
  const res = await fetch(`${API}/watchlist`);
  return res.json();
}

export async function triggerIndex(bgnDe?: string, endDe?: string): Promise<IndexResult[]> {
  const params = new URLSearchParams();
  if (bgnDe) params.set("bgn_de", bgnDe);
  if (endDe) params.set("end_de", endDe);
  const query = params.toString();
  const res = await fetch(`${API}/index${query ? `?${query}` : ""}`, { method: "POST" });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || "색인 실패");
  return data as IndexResult[];
}

export function sendChatMessage(question: string, corpCode: string): Promise<ChatResponse> {
  return postJSON(`${API}/chat`, { question, corpCode });
}
