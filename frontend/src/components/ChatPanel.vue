<template>
  <div class="card elev-sm panel">
    <div class="card-kicker">Step 02 · 질의응답</div>
    <div class="card-title">기업 정보 채팅</div>

    <div class="chat-log" ref="chatLogEl">
      <div v-for="(m, i) in messages" :key="i" :class="['chat-bubble', m.role]">{{ m.content }}</div>
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
import { ref, nextTick } from "vue";
import { sendChatMessage, type SourceItem } from "../api";

const props = defineProps<{
  corpCode: string;
}>();

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

const question = ref("");
const sending = ref(false);
const messages = ref<ChatMessage[]>([]);
const sources = ref<SourceItem[]>([]);
const chatLogEl = ref<HTMLDivElement | null>(null);

async function scrollToBottom() {
  await nextTick();
  if (chatLogEl.value) chatLogEl.value.scrollTop = chatLogEl.value.scrollHeight;
}

function onEnter(e: KeyboardEvent) {
  if (e.isComposing) return;
  sendChat();
}

async function sendChat() {
  if (sending.value) return;
  const q = question.value.trim();
  if (!q || !props.corpCode) return;
  sending.value = true;
  messages.value.push({ role: "user", content: q });
  question.value = "";
  scrollToBottom();
  try {
    const { answer, sources: srcs } = await sendChatMessage(q, props.corpCode);
    messages.value.push({ role: "assistant", content: answer });
    sources.value = srcs;
  } catch (err) {
    messages.value.push({ role: "assistant", content: `오류: ${(err as Error).message}` });
  } finally {
    sending.value = false;
    scrollToBottom();
  }
}
</script>
