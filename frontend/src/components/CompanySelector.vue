<template>
  <div class="card elev-sm panel">
    <div class="card-kicker">Step 01 · 기업 선택</div>
    <div class="card-title">대상 기업</div>

    <div class="field">
      <label for="company-select">대상 기업</label>
      <select id="company-select" class="input" :value="modelValue" @change="onSelect">
        <option v-for="c in companies" :key="c.corpCode" :value="c.corpCode">
          {{ c.name }} ({{ c.stockCode }})
        </option>
      </select>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { WatchedCompany } from "../api";

defineProps<{
  companies: WatchedCompany[];
  modelValue: string;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

function onSelect(event: Event) {
  emit("update:modelValue", (event.target as HTMLSelectElement).value);
}
</script>
