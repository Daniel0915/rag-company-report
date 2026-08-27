<template>
  <nav class="nav">
    <span class="nav-brand">
      <span class="brand-mark">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>
      </span>
      기업 리포트 챗봇 · 관리자
    </span>
    <a href="./index.html">챗봇으로 돌아가기</a>
  </nav>
  <main class="page">
    <header class="hero">
      <h1>문서 관리</h1>
      <p class="text-muted">
        DART 공시와 뉴스를 자동으로 가져오고, 실제로 저장된 데이터를 확인하고, PDF 문서를 추가로 업로드합니다.
      </p>
    </header>

    <div class="admin-stack">
      <DartIndexPanel />
      <NewsFetchPanel />
      <IndexedDataViewer :companies="companies" />
      <RelationshipExplorer :companies="companies" />
    </div>

    <div class="layout-grid">
      <AdminDocumentUpload :companies="companies" @uploaded="onUploaded" />
      <AdminDocumentList :documents="documents" @deleted="onDeleted" />
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import DartIndexPanel from "./components/DartIndexPanel.vue";
import NewsFetchPanel from "./components/NewsFetchPanel.vue";
import IndexedDataViewer from "./components/IndexedDataViewer.vue";
import RelationshipExplorer from "./components/RelationshipExplorer.vue";
import AdminDocumentUpload from "./components/AdminDocumentUpload.vue";
import AdminDocumentList from "./components/AdminDocumentList.vue";
import { fetchWatchlist, fetchAdminDocuments, type WatchedCompany, type AdminDocument } from "./api";

const companies = ref<WatchedCompany[]>([]);
const documents = ref<AdminDocument[]>([]);

onMounted(async () => {
  companies.value = await fetchWatchlist();
  documents.value = await fetchAdminDocuments();
});

function onUploaded(doc: AdminDocument) {
  documents.value = [doc, ...documents.value];
}

function onDeleted(id: string) {
  documents.value = documents.value.filter((d) => d.id !== id);
}
</script>
