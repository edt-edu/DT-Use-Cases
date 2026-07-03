<script setup lang="ts">
import type { MonitoringAasProperty } from '../services/monitoringApi'

defineProps<{
  properties: MonitoringAasProperty[]
}>()

function renderValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<template>
  <section class="card side-card">
    <p class="eyebrow">AAS properties</p>
    <h3>{{ properties.length }} properties</h3>
    <div v-if="properties.length === 0" class="empty">No AAS properties available yet.</div>
    <article v-for="property in properties.slice(0, 12)" :key="`${property.shellId}-${property.submodelId}-${property.idShortPath}`" class="aas-row">
      <strong>{{ property.idShortPath }}</strong>
      <span class="mono">{{ renderValue(property.value) }}</span>
      <small>{{ property.submodelId }} · {{ property.valueType }}</small>
    </article>
  </section>
</template>
