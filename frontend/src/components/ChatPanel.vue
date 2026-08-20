<template>
  <section class="step">
    <h2>등록 문서 기반 채팅</h2>

    <label>문서 종류 필터</label>
    <select v-model="filterDocType">
      <option value="">전체</option>
      <option v-for="dt in docTypes" :key="dt" :value="dt">{{ dt }}</option>
    </select>

    <label>분야 필터</label>
    <select v-model="filterDomain">
      <option value="">전체</option>
      <option v-for="d in domains" :key="d.value" :value="d.value">{{ d.label }}</option>
    </select>

    <div class="chat-log" ref="chatLogEl">
      <div v-for="(m, i) in messages" :key="i" :class="['chat-bubble', m.role]">{{ m.content }}</div>
    </div>

    <input
      type="text"
      v-model="question"
      placeholder="등록한 정책 문서에 대해 질문하세요..."
      @keydown.enter="sendChat"
    />
    <button :disabled="sending" @click="sendChat">전송</button>

    <h3 class="muted">출처</h3>
    <div>
      <div v-for="(s, i) in sources" :key="i" class="source-item">
        [{{ s.docType ?? "-" }} / {{ s.chunkStrategy ?? "-" }}] {{ s.sourceFile }}
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, nextTick } from "vue";
import { sendChatMessage, type DomainOption, type SourceItem } from "../api";

defineProps<{
  docTypes: string[];
  domains: DomainOption[];
}>();

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

const filterDocType = ref("");
const filterDomain = ref("");
const question = ref("");
const sending = ref(false);
const messages = ref<ChatMessage[]>([]);
const sources = ref<SourceItem[]>([]);
const chatLogEl = ref<HTMLDivElement | null>(null);

async function scrollToBottom() {
  await nextTick();
  if (chatLogEl.value) chatLogEl.value.scrollTop = chatLogEl.value.scrollHeight;
}

async function sendChat() {
  const q = question.value.trim();
  if (!q) return;
  sending.value = true;
  messages.value.push({ role: "user", content: q });
  question.value = "";
  scrollToBottom();
  try {
    const filter = { doc_type: filterDocType.value, domain: filterDomain.value };
    const { answer, sources: srcs } = await sendChatMessage(q, filter);
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
