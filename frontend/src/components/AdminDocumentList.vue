<template>
  <div class="card elev-sm panel">
    <div class="card-kicker">Step 05 · 업로드된 문서</div>
    <div class="card-title">등록된 문서 목록</div>

    <p v-if="!documents.length" class="text-muted">아직 업로드된 문서가 없습니다.</p>

    <div class="admin-doc-list">
      <div v-for="d in documents" :key="d.id" class="admin-doc-row">
        <div class="admin-doc-info">
          <div class="admin-doc-title">{{ d.title }}</div>
          <div class="admin-doc-meta">
            <span class="tag tag-outline">{{ d.corpName }}</span>
            <span v-if="d.category" class="tag tag-neutral">{{ d.category }}</span>
            <span v-if="d.docDate" class="text-muted">{{ d.docDate }}</span>
            <span class="text-muted">{{ d.chunkCount }}청크</span>
          </div>
          <p v-if="d.description" class="admin-doc-desc">{{ d.description }}</p>
        </div>
        <button
          type="button"
          class="btn btn-ghost"
          :disabled="deletingId === d.id"
          @click="remove(d.id)"
        >
          {{ deletingId === d.id ? "삭제 중..." : "삭제" }}
        </button>
      </div>
    </div>
    <p v-if="error" class="error-text">오류: {{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { deleteAdminDocument, type AdminDocument } from "../api";

defineProps<{
  documents: AdminDocument[];
}>();

const emit = defineEmits<{
  deleted: [id: string];
}>();

const deletingId = ref("");
const error = ref("");

async function remove(id: string) {
  if (!confirm("이 문서를 삭제할까요? 저장된 내용도 함께 제거됩니다.")) return;
  deletingId.value = id;
  error.value = "";
  try {
    await deleteAdminDocument(id);
    emit("deleted", id);
  } catch (err) {
    error.value = (err as Error).message;
  } finally {
    deletingId.value = "";
  }
}
</script>
