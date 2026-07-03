<script setup lang="ts">
import { computed, ref } from 'vue'
import type { MachineDataPoint } from '../services/monitoringApi'

const props = defineProps<{
  dataPoints: MachineDataPoint[]
}>()

const filter = ref('')
const showOnlyWithValues = ref(false)

const filteredDataPoints = computed(() => {
  const normalized = filter.value.trim().toLowerCase()
  return props.dataPoints.filter((point) => {
    const valueMatch = !showOnlyWithValues.value || point.latestValue !== null && point.latestValue !== undefined
    if (!normalized) return valueMatch
    const haystack = [point.id, point.description, point.machineId, point.topic, point.valueType, String(point.latestValue ?? '')]
      .join(' ')
      .toLowerCase()
    return valueMatch && haystack.includes(normalized)
  })
})

function renderValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function formatTime(value: string | null): string {
  return value ? new Date(value).toLocaleTimeString() : '—'
}
</script>

<template>
  <section class="card table-card">
    <div class="table-header">
      <div>
        <p class="eyebrow">Machine data</p>
        <h3>{{ filteredDataPoints.length }} data points</h3>
      </div>
      <div class="table-actions">
        <label class="toggle">
          <input v-model="showOnlyWithValues" type="checkbox" />
          only values
        </label>
        <input v-model="filter" class="search" type="search" placeholder="Filter data points..." />
      </div>
    </div>

    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Data point</th>
            <th>Topic</th>
            <th>Description</th>
            <th>Type</th>
            <th>Value</th>
            <th>Quality</th>
            <th>Updated</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="point in filteredDataPoints" :key="point.id">
            <td class="mono strong">{{ point.id }}</td>
            <td class="mono muted">{{ point.topic ?? '—' }}</td>
            <td>{{ point.description || '—' }}</td>
            <td>{{ point.valueType ?? '—' }}</td>
            <td class="mono value-cell">{{ renderValue(point.latestValue) }}</td>
            <td><span class="quality">{{ point.quality ?? '—' }}</span></td>
            <td>{{ formatTime(point.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
