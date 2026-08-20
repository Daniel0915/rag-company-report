<template>
  <section class="step">
    <h2>정책/지침 문서 등록 + 메타데이터 라벨링</h2>
    <input type="file" accept=".pdf" @change="onFileChange" />

    <label>문서 종류</label>
    <select v-model="docType">
      <option v-for="dt in docTypes" :key="dt" :value="dt">{{ dt }}</option>
    </select>

    <label>관련 분야 (인증기준 대분류)</label>
    <select v-model="domain">
      <option v-for="d in domains" :key="d.value" :value="d.value">{{ d.label }}</option>
    </select>

    <label>연도</label>
    <input type="text" v-model="year" />

    <button :disabled="uploading" @click="upload">등록</button>
    <button style="background: #a33" @click="deleteAll">전체 삭제</button>

    <div class="result">{{ resultText }}</div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { uploadDocument, deleteAllDocuments, type DomainOption } from "../api";

const props = defineProps<{
  docTypes: string[];
  domains: DomainOption[];
}>();

const file = ref<File | null>(null);
const docType = ref("");
const domain = ref("");
const year = ref("2026");
const uploading = ref(false);
const resultText = ref("");

watch(
  () => props.docTypes,
  (v) => {
    if (v.length && !docType.value) docType.value = v[0];
  },
);
watch(
  () => props.domains,
  (v) => {
    if (v.length && !domain.value) domain.value = v[0].value;
  },
);

function onFileChange(e: Event) {
  const target = e.target as HTMLInputElement;
  file.value = target.files?.[0] ?? null;
}

async function upload() {
  if (!file.value) {
    alert("파일을 선택하세요.");
    return;
  }
  uploading.value = true;
  resultText.value = "처리 중...";
  try {
    const data = await uploadDocument(file.value, docType.value, domain.value, year.value);
    resultText.value = `${data.filename}: ${data.status} (${data.chunks} 청크)`;
  } catch (err) {
    resultText.value = `오류: ${(err as Error).message}`;
  } finally {
    uploading.value = false;
  }
}

async function deleteAll() {
  await deleteAllDocuments();
  resultText.value = "모든 문서가 삭제되었습니다.";
}
</script>
