<template>
  <header>
    <h1>기업 리포트 챗봇 — DART 공시 기반</h1>
  </header>
  <main>
    <CompanySelector :companies="companies" v-model="selectedCorpCode" />
    <ChatPanel :corp-code="selectedCorpCode" />
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
