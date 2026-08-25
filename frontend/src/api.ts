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

export interface IndexedChunk {
  id: string;
  corpCode: string;
  corpName: string;
  reportNm: string;
  rceptNo: string;
  pblntfTy: string;
  docType: string;
  sectionTitle: string;
  rceptDt: string;
  textPreview: string;
}

export interface IndexedChunkPage {
  items: IndexedChunk[];
  total: number;
}

export interface FilerDisclosure {
  filerName: string;
  reportNm: string;
  rceptDt: string;
  rceptNo: string;
}

export interface RelatedCompany {
  corpCode: string;
  corpName: string;
}

export interface AdminDocument {
  id: string;
  corpCode: string;
  corpName: string;
  title: string;
  category: string;
  description: string;
  docDate: string;
  fileName: string;
  uploadedAt: string;
  chunkCount: number;
}

const API = "/api/company-report";
const ADMIN_API = "/api/admin/documents";

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
  if (!res.ok) throw new Error(data.error || "공시 가져오기 실패");
  return data as IndexResult[];
}

export interface ChatTurn {
  role: "user" | "assistant";
  content: string;
}

export type ChatProvider = "local" | "cloud";

export function sendChatMessage(
  question: string,
  corpCode: string,
  history: ChatTurn[] = [],
  topK?: number,
  provider: ChatProvider = "local"
): Promise<ChatResponse> {
  return postJSON(`${API}/chat`, { question, corpCode, history, topK, provider });
}

export async function fetchAdminDocuments(): Promise<AdminDocument[]> {
  const res = await fetch(ADMIN_API);
  if (!res.ok) throw new Error("문서 목록을 불러오지 못했습니다");
  return res.json();
}

export async function uploadAdminDocument(formData: FormData): Promise<AdminDocument> {
  const res = await fetch(ADMIN_API, { method: "POST", body: formData });
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || "업로드 실패");
  return data as AdminDocument;
}

export async function fetchIndexedChunks(params: {
  corpCode?: string;
  sourceType?: string;
  limit: number;
  offset: number;
}): Promise<IndexedChunkPage> {
  const q = new URLSearchParams();
  if (params.corpCode) q.set("corpCode", params.corpCode);
  if (params.sourceType) q.set("sourceType", params.sourceType);
  q.set("limit", String(params.limit));
  q.set("offset", String(params.offset));
  const res = await fetch(`/api/admin/indexed-chunks?${q.toString()}`);
  if (!res.ok) throw new Error("저장된 데이터를 불러오지 못했습니다");
  return res.json();
}

export async function fetchFilers(corpCode: string): Promise<FilerDisclosure[]> {
  const res = await fetch(`/api/admin/graph/filers?corpCode=${encodeURIComponent(corpCode)}`);
  if (!res.ok) throw new Error("제출인 목록을 불러오지 못했습니다");
  return res.json();
}

export async function fetchRelatedCompanies(filerName: string): Promise<RelatedCompany[]> {
  const res = await fetch(`/api/admin/graph/related-companies?filerName=${encodeURIComponent(filerName)}`);
  if (!res.ok) throw new Error("관련 기업을 불러오지 못했습니다");
  return res.json();
}

export async function deleteAdminDocument(id: string): Promise<void> {
  const res = await fetch(`${ADMIN_API}/${id}`, { method: "DELETE" });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}) as { error?: string });
    throw new Error(data.error || "삭제 실패");
  }
}
