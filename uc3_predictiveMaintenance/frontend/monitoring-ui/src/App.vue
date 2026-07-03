<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AasPropertyList from './components/AasPropertyList.vue'
import EventList from './components/EventList.vue'
import KpiCards from './components/KpiCards.vue'
import MachineDataTable from './components/MachineDataTable.vue'
import MappingList from './components/MappingList.vue'
import ServiceRegistry from './components/ServiceRegistry.vue'
import StateCard from './components/StateCard.vue'
import { fetchSnapshot, type MonitoringDashboardSnapshot } from './services/monitoringApi'

const snapshot = ref<MonitoringDashboardSnapshot | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const autoRefresh = ref(true)
const refreshIntervalMs = ref(2000)
let timer: number | undefined

const lastUpdated = computed(() => {
  if (!snapshot.value?.timestamp) return 'never'
  return new Date(snapshot.value.timestamp).toLocaleString()
})

async function refresh() {
  loading.value = true
  error.value = null
  try {
    snapshot.value = await fetchSnapshot()
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    loading.value = false
  }
}

function scheduleRefresh() {
  window.clearInterval(timer)
  if (autoRefresh.value) {
    timer = window.setInterval(refresh, refreshIntervalMs.value)
  }
}

onMounted(async () => {
  await refresh()
  scheduleRefresh()
})

onBeforeUnmount(() => {
  window.clearInterval(timer)
})
</script>

<template>
  <main class="app-shell">
    <header class="hero">
      <div>
        <p class="eyebrow">Digital Twin monitoring</p>
        <h1>Machine state and runtime data</h1>
        <p class="hero-copy">
          REST-connected dashboard for the conveyor state, MAPE-K KPIs, service registry and AAS model properties.
        </p>
      </div>
      <div class="refresh-panel">
        <button :disabled="loading" @click="refresh">
          {{ loading ? 'Refreshing...' : 'Refresh now' }}
        </button>
        <label class="toggle">
          <input v-model="autoRefresh" type="checkbox" @change="scheduleRefresh" />
          auto-refresh
        </label>
        <span>Last update: {{ lastUpdated }}</span>
      </div>
    </header>

    <div v-if="error" class="error-box">
      {{ error }}
    </div>

    <template v-if="snapshot">
      <section v-if="snapshot.messages.length" class="message-list">
        <div v-for="message in snapshot.messages" :key="message" class="message">{{ message }}</div>
      </section>

      <section class="dashboard-grid">
        <div class="main-column">
          <StateCard :state="snapshot.conveyor" />
          <KpiCards :kpis="snapshot.kpis" />
          <MachineDataTable :data-points="snapshot.machineData" />
        </div>
        <aside class="side-column">
          <ServiceRegistry :services="snapshot.services" />
          <MappingList :mappings="snapshot.mappings" />
          <EventList :events="snapshot.recentEvents" />
          <AasPropertyList :properties="snapshot.aasProperties" />
        </aside>
      </section>
    </template>

    <div v-else-if="loading" class="loading-card">Loading monitoring snapshot...</div>
  </main>
</template>
