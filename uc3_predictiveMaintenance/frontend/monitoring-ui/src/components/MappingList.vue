<script setup lang="ts">
import type { MonitoringMappingDescriptor } from '../services/monitoringApi'

defineProps<{
  mappings: MonitoringMappingDescriptor[]
}>()
</script>

<template>
  <section class="card compact-card">
    <div class="card-header">
      <div>
        <p class="eyebrow">Mappings</p>
        <h2>Synchronization wiring</h2>
      </div>
      <span class="pill">{{ mappings.length }}</span>
    </div>
    <div v-if="mappings.length" class="mini-list">
      <article v-for="mapping in mappings.slice(0, 12)" :key="mapping.mappingId" class="mini-list-row">
        <strong>{{ mapping.mappingId }}</strong>
        <span>{{ mapping.direction }}</span>
        <code>{{ mapping.source }} → {{ mapping.target }}</code>
      </article>
    </div>
    <p v-else class="empty-text">No mappings registered yet.</p>
  </section>
</template>
