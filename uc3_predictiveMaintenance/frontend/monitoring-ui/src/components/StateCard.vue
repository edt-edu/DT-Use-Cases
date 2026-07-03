<script setup lang="ts">
import type { ConveyorMonitoringState } from '../services/monitoringApi'

const props = defineProps<{
  state: ConveyorMonitoringState
}>()

function formatNumber(value: number | null, digits = 2): string {
  return value === null || Number.isNaN(value) ? '—' : value.toFixed(digits)
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleString() : 'No update yet'
}
</script>

<template>
  <section class="card state-card" :class="`state-${props.state.severity}`">
    <div class="state-header">
      <div>
        <p class="eyebrow">Conveyor state</p>
        <h2>{{ props.state.state }}</h2>
      </div>
      <span class="state-badge">{{ props.state.severity }}</span>
    </div>

    <div class="integrity">
      <div class="integrity-row">
        <span>Integrity</span>
        <strong>{{ formatNumber(props.state.integrityPercent, 1) }}%</strong>
      </div>
      <div class="bar">
        <div class="bar-fill" :style="{ width: `${Math.max(0, Math.min(100, props.state.integrityPercent ?? 0))}%` }" />
      </div>
    </div>

    <dl class="state-metrics">
      <div>
        <dt>Transport time</dt>
        <dd>{{ props.state.transportTimeMillis ?? '—' }} ms</dd>
      </div>
      <div>
        <dt>Payload</dt>
        <dd>{{ formatNumber(props.state.payloadWeightKg, 2) }} kg</dd>
      </div>
      <div>
        <dt>Productivity</dt>
        <dd>{{ formatNumber(props.state.productivityKgPerSecond, 4) }} kg/s</dd>
      </div>
    </dl>

    <p class="timestamp">Updated: {{ formatTime(props.state.updatedAt) }}</p>
  </section>
</template>
