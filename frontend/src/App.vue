<template>
  <header>
    <h1>ISMS-P 인증 준비 챗봇 — Spring Boot + Spring AI</h1>
  </header>
  <main>
    <DocumentUpload :doc-types="docTypes" :domains="domains" />
    <ChatPanel :doc-types="docTypes" :domains="domains" />
  </main>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import DocumentUpload from "./components/DocumentUpload.vue";
import ChatPanel from "./components/ChatPanel.vue";
import { fetchMetadataOptions, type DomainOption } from "./api";

const docTypes = ref<string[]>([]);
const domains = ref<DomainOption[]>([]);

onMounted(async () => {
  const options = await fetchMetadataOptions();
  docTypes.value = options.docTypes;
  domains.value = options.domains;
});
</script>
