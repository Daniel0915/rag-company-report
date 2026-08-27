<template>
  <nav class="nav">
    <span class="nav-brand">
      <span class="brand-mark">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>
      </span>
      기업 리포트 챗봇
    </span>
    <a href="#" aria-current="page">DART 공시 + 뉴스 기반</a>
    <a href="./admin.html">관리자</a>
  </nav>
  <main class="page">
    <header class="hero">
      <h1>기업 리포트 챗봇</h1>
      <p class="text-muted">관심 기업의 DART 공시(사업보고서 등)와 최신 뉴스를 가져와 저장하고, AI에게 바로 물어보세요.</p>
    </header>
    <div class="layout-grid">
      <CompanySelector :companies="companies" v-model="selectedCorpCode" />
      <ChatPanel :corp-code="selectedCorpCode" />
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import CompanySelector from "./components/CompanySelector.vue";
import ChatPanel from "./components/ChatPanel.vue";
import { fetchWatchlist, type WatchedCompany } from "./api";

const companies = ref<WatchedCompany[]>([]);
const selectedCorpCode = ref("");

onMounted(async () => {
  companies.value = await fetchWatchlist();
  if (companies.value.length > 0) {
    selectedCorpCode.value = companies.value[0].corpCode;
  }
});
</script>
