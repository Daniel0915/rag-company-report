import type { SourceItem } from "./api";

export interface StoredChatMessage {
  id?: number;
  corpCode: string;
  role: "user" | "assistant";
  content: string;
  sources?: SourceItem[];
  createdAt: number;
}

const DB_NAME = "company-report-chat-history";
const DB_VERSION = 1;
const STORE_NAME = "messages";

let dbPromise: Promise<IDBDatabase> | null = null;

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, { keyPath: "id", autoIncrement: true });
        store.createIndex("corpCode", "corpCode", { unique: false });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}

export async function saveMessage(message: Omit<StoredChatMessage, "id">): Promise<void> {
  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, "readwrite");
    tx.objectStore(STORE_NAME).add(message);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

/** 특정 기업의 저장된 대화 전체(시간순)를 가져온다. 채팅창을 다시 열었을 때 복원용. */
export async function getMessages(corpCode: string): Promise<StoredChatMessage[]> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, "readonly");
    const index = tx.objectStore(STORE_NAME).index("corpCode");
    const results: StoredChatMessage[] = [];
    const req = index.openCursor(IDBKeyRange.only(corpCode));
    req.onsuccess = () => {
      const cursor = req.result;
      if (cursor) {
        results.push(cursor.value as StoredChatMessage);
        cursor.continue();
      } else {
        results.sort((a, b) => a.createdAt - b.createdAt);
        resolve(results);
      }
    };
    req.onerror = () => reject(req.error);
  });
}

/** 최근 N개 메시지를 role/content만 추려서 백엔드로 보낼 히스토리로 만든다. */
export async function getRecentHistory(
  corpCode: string,
  limit: number
): Promise<{ role: "user" | "assistant"; content: string }[]> {
  const all = await getMessages(corpCode);
  return all.slice(-limit).map((m) => ({ role: m.role, content: m.content }));
}
