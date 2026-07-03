<script setup lang="ts">
import type { MonitoringEventDescriptor } from '../services/monitoringApi'

defineProps<{
  events: MonitoringEventDescriptor[]
}>()
</script>

<template>
  <section class="card compact-card">
    <div class="card-header">
      <div>
        <p class="eyebrow">Events</p>
        <h2>Recent engine events</h2>
      </div>
      <span class="pill">{{ events.length }}</span>
    </div>
    <div v-if="events.length" class="mini-list">
      <article v-for="event in events.slice(0, 12)" :key="event.id" class="mini-list-row">
        <strong>{{ event.type }}</strong>
        <span>{{ event.source }} · {{ event.severity }}</span>
        <small>{{ new Date(event.timestamp).toLocaleTimeString() }}</small>
      </article>
    </div>
    <p v-else class="empty-text">No events recorded yet.</p>
  </section>
</template>
