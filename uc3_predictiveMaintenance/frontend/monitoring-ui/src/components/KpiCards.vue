<script setup lang="ts">
import type { KpiValue } from '../services/monitoringApi'

defineProps<{
  kpis: KpiValue[]
}>()

function renderValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : value.toFixed(4)
  return String(value)
}
</script>

<template>
  <section class="kpi-grid">
    <article v-for="kpi in kpis" :key="kpi.id" class="card kpi-card">
      <p class="eyebrow">{{ kpi.label }}</p>
      <div class="kpi-value">
        {{ renderValue(kpi.value) }}
        <span v-if="kpi.unit">{{ kpi.unit }}</span>
      </div>
      <p class="kpi-id">{{ kpi.id }}</p>
    </article>
  </section>
</template>
