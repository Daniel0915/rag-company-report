<template>
  <div class="card elev-sm panel">
    <div class="card-kicker">Step 03 · 지분 관계 탐색</div>
    <div class="card-title">누가 지분을 공시했나 (Graph RAG)</div>
    <p class="card-body">
      벡터 검색만으로는 안 되는 관계 탐색입니다. 기업을 고르면 그 기업에 지분을 공시한
      사람/기관을 보여주고, 그 사람/기관을 클릭하면 다른 어떤 회사에도 지분을 공시했는지
      이어서 보여줍니다.
    </p>

    <div class="field">
      <label for="rel-company">기업</label>
      <select id="rel-company" class="input" v-model="corpCode" @change="loadFilers">
        <option value="" disabled>기업 선택</option>
        <option v-for="c in companies" :key="c.corpCode" :value="c.corpCode">
          {{ c.name }} ({{ c.stockCode }})
        </option>
      </select>
    </div>

    <p v-if="loadingFilers" class="text-muted">불러오는 중...</p>
    <p v-else-if="corpCode && !filers.length" class="text-muted">
      이 기업에 대해 그래프에 저장된 지분공시 제출인이 아직 없습니다.
    </p>

    <div v-if="filers.length" class="admin-doc-list">
      <div v-for="f in filers" :key="f.rceptNo + f.filerName" class="admin-doc-row">
        <div class="admin-doc-info">
          <button type="button" class="admin-doc-title admin-doc-link" @click="loadRelated(f.filerName)">
            {{ f.filerName }}
          </button>
          <div class="admin-doc-meta">
            <span class="text-muted">{{ f.reportNm }} ({{ f.rceptNo }})</span>
            <span v-if="f.rceptDt" class="text-muted">{{ f.rceptDt }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="selectedFiler" class="related-panel">
      <h6 class="text-muted section-label">"{{ selectedFiler }}"가 지분을 공시한 다른 기업</h6>
      <p v-if="loadingRelated" class="text-muted">불러오는 중...</p>
      <p v-else-if="!related.length" class="text-muted">다른 기업에는 지분을 공시한 기록이 없습니다.</p>
      <div v-else class="admin-filter-row" style="flex-wrap: wrap">
        <span v-for="r in related" :key="r.corpCode" class="tag tag-outline">{{ r.corpName }}</span>
      </div>
    </div>
    <p v-if="error" class="error-text">오류: {{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { fetchFilers, fetchRelatedCompanies, type FilerDisclosure, type RelatedCompany, type WatchedCompany } from "../api";

defineProps<{
  companies: WatchedCompany[];
}>();

const corpCode = ref("");
const filers = ref<FilerDisclosure[]>([]);
const loadingFilers = ref(false);

const selectedFiler = ref("");
const related = ref<RelatedCompany[]>([]);
const loadingRelated = ref(false);

const error = ref("");

async function loadFilers() {
  selectedFiler.value = "";
  related.value = [];
  if (!corpCode.value) return;
  loadingFilers.value = true;
  error.value = "";
  try {
    filers.value = await fetchFilers(corpCode.value);
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    loadingFilers.value = false;
  }
}

async function loadRelated(filerName: string) {
  selectedFiler.value = filerName;
  loadingRelated.value = true;
  error.value = "";
  try {
    related.value = await fetchRelatedCompanies(filerName);
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    loadingRelated.value = false;
  }
}
</script>
