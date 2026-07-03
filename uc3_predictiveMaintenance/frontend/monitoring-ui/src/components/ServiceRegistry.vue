<script setup lang="ts">
import type { MonitoringServiceDescriptor } from '../services/monitoringApi'

defineProps<{
  services: MonitoringServiceDescriptor[]
}>()
</script>

<template>
  <section class="card side-card">
    <p class="eyebrow">Service registry</p>
    <h3>{{ services.length }} services</h3>
    <div v-if="services.length === 0" class="empty">No services registered yet.</div>
    <article v-for="service in services" :key="service.serviceId" class="service-row">
      <div class="service-title">
        <strong>{{ service.serviceId }}</strong>
        <span :class="['ready-badge', service.ready === false ? 'not-ready' : 'ready']">
          {{ service.ready === null ? 'unknown' : service.ready ? 'ready' : 'missing data' }}
        </span>
      </div>
      <p>{{ service.description || service.serviceType }}</p>
      <small>Produces: {{ service.producedDataPoints.join(', ') || '—' }}</small>
      <small v-if="service.missingDataPoints.length > 0">Missing: {{ service.missingDataPoints.join(', ') }}</small>
    </article>
  </section>
</template>
