<template>
  <div class="card elev-sm panel">
    <div class="card-kicker">Step 01 · 기업 선택</div>
    <div class="card-title">대상 기업 &amp; 공시 갱신</div>

    <div class="field">
      <label for="company-select">대상 기업</label>
      <select id="company-select" class="input" :value="modelValue" @change="onSelect">
        <option v-for="c in companies" :key="c.corpCode" :value="c.corpCode">
          {{ c.name }} ({{ c.stockCode }})
        </option>
      </select>
    </div>

    <button type="button" class="btn btn-secondary btn-block" :disabled="indexing" @click="runIndex">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/></svg>
      {{ indexing ? "최신 공시 가져오는 중..." : "최신 공시 가져오기 (최근 2년)" }}
    </button>

    <div v-if="results.length" class="result-list">
      <div v-for="(r, i) in results" :key="i" class="result-row">
        [{{ r.corpName }}] {{ r.reportNm }} ({{ r.rceptNo }}) — {{ r.status }}, {{ r.chunks }}청크
      </div>
    </div>
    <p v-if="error" class="error-text">오류: {{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { triggerIndex, type IndexResult, type WatchedCompany } from "../api";

defineProps<{
  companies: WatchedCompany[];
  modelValue: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

const indexing = ref(false);
const results = ref<IndexResult[]>([]);
const error = ref("");

function onSelect(event: Event) {
  emit("update:modelValue", (event.target as HTMLSelectElement).value);
}

async function runIndex() {
  indexing.value = true;
  error.value = "";
  try {
    results.value = await triggerIndex();
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    indexing.value = false;
  }
}
</script>
