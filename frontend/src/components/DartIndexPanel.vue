<template>
  <div class="card elev-sm panel">
    <div class="card-kicker">Step 01 · 공시 가져오기</div>
    <div class="card-title">DART 공시 자동으로 가져오기</div>
    <p class="card-body">
      워치리스트 전체 기업의 정기공시(사업/반기/분기보고서)와 지분공시(주식등의대량보유상황보고서,
      임원ㆍ주요주주특정증권등소유상황보고서)를 DART Open API에서 받아와 저장합니다.
    </p>

    <button type="button" class="btn btn-secondary btn-block" :disabled="fetching" @click="runFetch">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/></svg>
      {{ fetching ? "최신 공시 가져오는 중..." : "최신 공시 가져오기 (최근 2년)" }}
    </button>

    <div v-if="results.length" class="result-list">
      <div v-for="(r, i) in results" :key="i" class="result-row">
        [{{ r.corpName }}] {{ r.reportNm }} ({{ r.rceptNo }}) — {{ statusLabel(r.status) }}, {{ r.chunks }}건 저장
      </div>
    </div>
    <p v-if="error" class="error-text">오류: {{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { triggerIndex, type IndexResult } from "../api";

const fetching = ref(false);
const results = ref<IndexResult[]>([]);
const error = ref("");

function statusLabel(status: string): string {
  if (status === "new") return "신규 저장";
  if (status === "updated") return "내용 갱신";
  if (status === "skipped") return "변경 없음";
  return status;
}

async function runFetch() {
  fetching.value = true;
  error.value = "";
  try {
    results.value = await triggerIndex();
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    fetching.value = false;
  }
}
</script>
