<template>
  <div class="card elev-sm panel">
    <div class="card-kicker">Step 02 · 질의응답</div>
    <div class="card-title">기업 정보 채팅</div>

    <div class="provider-control">
      <label>답변 모델</label>
      <div class="provider-toggle">
        <button
          type="button"
          :class="['provider-btn', { active: provider === 'local' }]"
          @click="provider = 'local'"
        >
          로컬 (qwen2.5:3b)
        </button>
        <button
          type="button"
          :class="['provider-btn', { active: provider === 'gemini' }]"
          @click="provider = 'gemini'"
        >
          Gemini
        </button>
        <button
          type="button"
          :class="['provider-btn', { active: provider === 'claude' }]"
          @click="provider = 'claude'"
        >
          Claude
        </button>
      </div>
    </div>

    <div class="topk-control">
      <label for="topk-slider">
        검색 범위(topK)
        <span class="topk-value">{{ topK }}</span>
      </label>
      <input id="topk-slider" type="range" min="1" max="20" v-model.number="topK" />
      <p class="topk-hint">
        질문마다 근거로 가져올 공시 조각 수예요. 낮으면 빠르지만 원하는 내용이 빠질 수 있고,
        높으면 더 폭넓게 찾지만 답변이 느려져요.
      </p>
    </div>

    <div class="chat-log" ref="chatLogEl">
      <div
        v-for="(m, i) in messages"
        :key="i"
        :class="['chat-bubble', m.role]"
      >
        <div v-if="m.role === 'assistant'" class="markdown-body" v-html="renderMarkdown(m.content)"></div>
        <template v-else>{{ m.content }}</template>
      </div>
      <div v-if="sending" class="chat-bubble assistant typing" aria-live="polite" aria-label="답변 생성 중">
        <span class="typing-dot"></span>
        <span class="typing-dot"></span>
        <span class="typing-dot"></span>
      </div>
    </div>

    <div class="chat-input-row">
      <input
        type="text"
        class="input"
        v-model="question"
        placeholder="선택한 기업의 공시(사업보고서/반기보고서 등)에 대해 질문하세요..."
        @keydown.enter="onEnter"
      />
      <button
        type="button"
        class="btn btn-primary btn-icon"
        :disabled="sending || !corpCode"
        @click="sendChat"
        aria-label="전송"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
      </button>
    </div>

    <h6 class="text-muted section-label">출처 공시</h6>
    <div class="source-list">
      <div v-for="(s, i) in sources" :key="i" class="source-item">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 9H8"/><path d="M16 13H8"/><path d="M16 17H8"/></svg>
        <span class="tag tag-outline">{{ s.corpName }}</span>
        <span class="source-text">{{ s.reportNm }} ({{ s.rceptNo }}) · {{ s.sectionTitle }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from "vue";
import { marked } from "marked";
import DOMPurify from "dompurify";
import { sendChatMessage, type ChatProvider, type SourceItem } from "../api";
import { getMessages, getRecentHistory, saveMessage } from "../chatHistory";

marked.setOptions({ breaks: true, gfm: true });

function renderMarkdown(content: string): string {
  const html = marked.parse(content, { async: false }) as string;
  return DOMPurify.sanitize(html);
}

const props = defineProps<{
  corpCode: string;
}>();

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

const HISTORY_LIMIT = 6;

const topK = ref(6);
const provider = ref<ChatProvider>("local");
const question = ref("");
const sending = ref(false);
const messages = ref<ChatMessage[]>([]);
const sources = ref<SourceItem[]>([]);
const chatLogEl = ref<HTMLDivElement | null>(null);

async function scrollToBottom() {
  await nextTick();
  if (chatLogEl.value) chatLogEl.value.scrollTop = chatLogEl.value.scrollHeight;
}

async function loadHistory(corpCode: string) {
  messages.value = [];
  sources.value = [];
  if (!corpCode) return;
  const stored = await getMessages(corpCode);
  messages.value = stored.map((m) => ({ role: m.role, content: m.content }));
  const last = stored[stored.length - 1];
  if (last?.sources) sources.value = last.sources;
  scrollToBottom();
}

watch(() => props.corpCode, (code) => loadHistory(code), { immediate: true });

function onEnter(e: KeyboardEvent) {
  if (e.isComposing) return;
  sendChat();
}

async function sendChat() {
  if (sending.value) return;
  const q = question.value.trim();
  if (!q || !props.corpCode) return;
  const corpCode = props.corpCode;
  sending.value = true;
  const history = await getRecentHistory(corpCode, HISTORY_LIMIT);
  messages.value.push({ role: "user", content: q });
  question.value = "";
  scrollToBottom();
  await saveMessage({ corpCode, role: "user", content: q, createdAt: Date.now() });
  try {
    const { answer, sources: srcs } = await sendChatMessage(q, corpCode, history, topK.value, provider.value);
    messages.value.push({ role: "assistant", content: answer });
    sources.value = srcs;
    await saveMessage({ corpCode, role: "assistant", content: answer, sources: srcs, createdAt: Date.now() });
  } catch (err) {
    const message = `오류: ${(err as Error).message}`;
    messages.value.push({ role: "assistant", content: message });
    await saveMessage({ corpCode, role: "assistant", content: message, createdAt: Date.now() });
  } finally {
    sending.value = false;
    scrollToBottom();
  }
}
</script>
