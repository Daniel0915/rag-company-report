<template>
  <div class="card elev-sm panel">
    <div class="card-kicker">Step 03 · 저장된 데이터 확인</div>
    <div class="card-title">저장된 데이터 보기</div>
    <p class="card-body">실제로 저장된 공시 내용을 기업/유형별로 확인합니다.</p>

    <div class="admin-filter-row">
      <div class="field">
        <label for="viewer-company">기업</label>
        <select id="viewer-company" class="input" v-model="corpCode" @change="reload">
          <option value="">전체</option>
          <option v-for="c in companies" :key="c.corpCode" :value="c.corpCode">
            {{ c.name }} ({{ c.stockCode }})
          </option>
        </select>
      </div>
      <div class="field">
        <label for="viewer-type">유형</label>
        <select id="viewer-type" class="input" v-model="sourceType" @change="reload">
          <option value="">전체</option>
          <option value="A">정기공시</option>
          <option value="D">지분공시</option>
          <option value="ADMIN_PDF">관리자 PDF</option>
          <option value="NEWS">뉴스</option>
        </select>
      </div>
    </div>

    <p v-if="loading && !items.length" class="text-muted">불러오는 중...</p>
    <p v-else-if="!items.length" class="text-muted">조건에 맞는 데이터가 없습니다.</p>

    <div class="admin-doc-list">
      <div v-for="c in items" :key="c.id" class="admin-doc-row">
        <div class="admin-doc-info">
          <div class="admin-doc-title">{{ c.sectionTitle || c.reportNm }}</div>
          <div class="admin-doc-meta">
            <span class="tag tag-outline">{{ c.corpName }}</span>
            <span class="tag tag-neutral">{{ typeLabel(c) }}</span>
            <span v-if="c.docType === 'NEWS'" class="text-muted"
              >{{ c.reportNm }} (<a :href="c.rceptNo" target="_blank" rel="noopener noreferrer">{{ c.rceptNo }}</a
              >)</span
            >
            <span v-else class="text-muted">{{ c.reportNm }} ({{ c.rceptNo }})</span>
            <span v-if="c.rceptDt" class="text-muted">{{ c.rceptDt }}</span>
          </div>
          <p class="admin-doc-desc">{{ c.textPreview }}…</p>
        </div>
      </div>
    </div>

    <button
      v-if="items.length < total"
      type="button"
      class="btn btn-secondary btn-block"
      :disabled="loading"
      @click="loadMore"
    >
      {{ loading ? "불러오는 중..." : `더 보기 (${items.length} / ${total})` }}
    </button>
    <p v-if="error" class="error-text">오류: {{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { fetchIndexedChunks, type IndexedChunk, type WatchedCompany } from "../api";

defineProps<{
  companies: WatchedCompany[];
}>();

const PAGE_SIZE = 20;

const corpCode = ref("");
const sourceType = ref("");
const items = ref<IndexedChunk[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref("");

function typeLabel(c: IndexedChunk): string {
  if (c.docType === "ADMIN_PDF") return "관리자 PDF";
  if (c.docType === "NEWS") return "뉴스";
  if (c.pblntfTy === "D") return "지분공시";
  return "정기공시";
}

async function load(offset: number) {
  loading.value = true;
  error.value = "";
  try {
    const page = await fetchIndexedChunks({
      corpCode: corpCode.value || undefined,
      sourceType: sourceType.value || undefined,
      limit: PAGE_SIZE,
      offset,
    });
    items.value = offset === 0 ? page.items : [...items.value, ...page.items];
    total.value = page.total;
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    loading.value = false;
  }
}

function reload() {
  load(0);
}

function loadMore() {
  load(items.value.length);
}

onMounted(() => load(0));
</script>
