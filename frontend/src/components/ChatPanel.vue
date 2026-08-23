<template>
  <section class="step">
    <h2>기업 정보 채팅</h2>

    <div class="chat-log" ref="chatLogEl">
      <div v-for="(m, i) in messages" :key="i" :class="['chat-bubble', m.role]">{{ m.content }}</div>
    </div>

    <input
      type="text"
      v-model="question"
      placeholder="선택한 기업의 공시(사업보고서/반기보고서 등)에 대해 질문하세요..."
      @keydown.enter="sendChat"
    />
    <button :disabled="sending || !corpCode" @click="sendChat">전송</button>

    <h3 class="muted">출처</h3>
    <div>
      <div v-for="(s, i) in sources" :key="i" class="source-item">
        [{{ s.corpName }} / {{ s.reportNm }} ({{ s.rceptNo }})] {{ s.sectionTitle }}
      </div>
    </div>
  </section>
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

async function sendChat() {
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
