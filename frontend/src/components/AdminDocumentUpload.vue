<template>
  <div class="card elev-sm panel">
    <div class="card-kicker">Step 03 · 문서 업로드</div>
    <div class="card-title">PDF 문서 등록</div>

    <form @submit.prevent="submit">
      <div class="field">
        <label for="admin-company">대상 기업</label>
        <select id="admin-company" class="input" v-model="corpCode" required>
          <option value="" disabled>기업 선택</option>
          <option v-for="c in companies" :key="c.corpCode" :value="c.corpCode">
            {{ c.name }} ({{ c.stockCode }})
          </option>
        </select>
      </div>

      <div class="field">
        <label for="admin-title">제목/문서명</label>
        <input
          id="admin-title"
          type="text"
          class="input"
          v-model="title"
          placeholder="예: 2026년 3분기 실적 발표자료"
          required
        />
      </div>

      <div class="field">
        <label for="admin-category">카테고리/문서유형</label>
        <input
          id="admin-category"
          type="text"
          class="input"
          v-model="category"
          list="admin-category-options"
          placeholder="예: IR자료, 뉴스, 리서치 리포트"
        />
        <datalist id="admin-category-options">
          <option value="사업보고서" />
          <option value="IR자료" />
          <option value="뉴스" />
          <option value="리서치 리포트" />
          <option value="기타" />
        </datalist>
      </div>

      <div class="field">
        <label for="admin-doc-date">문서 날짜</label>
        <input id="admin-doc-date" type="date" class="input" v-model="docDate" />
      </div>

      <div class="field">
        <label for="admin-description">설명/비고</label>
        <input
          id="admin-description"
          type="text"
          class="input"
          v-model="description"
          placeholder="선택 입력"
        />
      </div>

      <div class="field">
        <label for="admin-file">PDF 파일</label>
        <input
          id="admin-file"
          type="file"
          class="input"
          accept="application/pdf"
          @change="onFileChange"
          required
        />
      </div>

      <button type="submit" class="btn btn-primary btn-block" :disabled="uploading || !file">
        {{ uploading ? "업로드 및 저장 중..." : "업로드" }}
      </button>
    </form>

    <p v-if="error" class="error-text">오류: {{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { uploadAdminDocument, type AdminDocument, type WatchedCompany } from "../api";

defineProps<{
  companies: WatchedCompany[];
}>();

const emit = defineEmits<{
  uploaded: [doc: AdminDocument];
}>();

const corpCode = ref("");
const title = ref("");
const category = ref("");
const docDate = ref("");
const description = ref("");
const file = ref<File | null>(null);
const uploading = ref(false);
const error = ref("");

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  file.value = input.files?.[0] ?? null;
}

async function submit() {
  if (!file.value || !corpCode.value || !title.value) return;
  uploading.value = true;
  error.value = "";
  try {
    const formData = new FormData();
    formData.append("file", file.value);
    formData.append("corpCode", corpCode.value);
    formData.append("title", title.value);
    if (category.value) formData.append("category", category.value);
    if (description.value) formData.append("description", description.value);
    if (docDate.value) formData.append("docDate", docDate.value);

    const doc = await uploadAdminDocument(formData);
    emit("uploaded", doc);

    title.value = "";
    category.value = "";
    docDate.value = "";
    description.value = "";
    file.value = null;
    const fileInput = document.getElementById("admin-file") as HTMLInputElement | null;
    if (fileInput) fileInput.value = "";
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    uploading.value = false;
  }
}
</script>
