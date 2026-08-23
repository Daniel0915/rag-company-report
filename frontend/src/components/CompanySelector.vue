<template>
  <section class="step">
    <h2>기업 선택 / 공시 갱신</h2>

    <label>대상 기업</label>
    <select :value="modelValue" @change="onSelect">
      <option v-for="c in companies" :key="c.corpCode" :value="c.corpCode">
        {{ c.name }} ({{ c.stockCode }})
      </option>
    </select>

    <button :disabled="indexing" @click="runIndex">
      {{ indexing ? "최신 공시 가져오는 중..." : "최신 공시 가져오기 (최근 2년)" }}
    </button>

    <div v-if="results.length" class="result">
      <div v-for="(r, i) in results" :key="i">
        [{{ r.corpName }}] {{ r.reportNm }} ({{ r.rceptNo }}) — {{ r.status }}, {{ r.chunks }}청크
      </div>
    </div>
    <div v-if="error" class="result">오류: {{ error }}</div>
  </section>
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
