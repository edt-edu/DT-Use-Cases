export interface MonitoringDashboardSnapshot {
  timestamp: string
  conveyor: ConveyorMonitoringState
  kpis: KpiValue[]
  machineData: MachineDataPoint[]
  services: MonitoringServiceDescriptor[]
  aasProperties: MonitoringAasProperty[]
  mappings: MonitoringMappingDescriptor[]
  recentEvents: MonitoringEventDescriptor[]
  messages: string[]
}

export interface ConveyorMonitoringState {
  state: string
  severity: 'normal' | 'warning' | 'emergency' | 'unknown' | string
  integrityPercent: number | null
  transportTimeMillis: number | null
  productivityKgPerSecond: number | null
  payloadWeightKg: number | null
  updatedAt: string | null
}

export interface KpiValue {
  id: string
  label: string
  value: unknown
  unit: string | null
  updatedAt: string | null
}

export interface MachineDataPoint {
  id: string
  description: string
  machineId: string | null
  topic: string | null
  valueType: string | null
  latestValue: unknown
  quality: string | null
  updatedAt: string | null
  sourceComponent: string | null
  metadata: Record<string, string>
}

export interface MonitoringServiceDescriptor {
  serviceId: string
  componentId: string
  serviceType: string
  description: string
  requiredDataPoints: string[]
  requiredModelProperties: string[]
  requiredFunctions: string[]
  producedDataPoints: string[]
  ready: boolean | null
  missingDataPoints: string[]
  metadata: Record<string, string>
  updatedAt: string | null
}

export interface MonitoringMappingDescriptor {
  mappingId: string
  source: string
  target: string
  direction: string
  transformation: string
  enabled: boolean
  metadata: Record<string, string>
}

export interface MonitoringEventDescriptor {
  id: string
  type: string
  source: string
  severity: string
  timestamp: string
  correlationId: string | null
  payload: Record<string, unknown>
}

export interface MonitoringAasProperty {
  shellId: string
  submodelId: string
  idShortPath: string
  idShort: string
  value: unknown
  valueType: string
  semanticId: string | null
  category: string | null
  description: string
  observedAt: string
  metadata: Record<string, string>
}

const API_BASE = import.meta.env.VITE_DT_MONITORING_API_BASE ?? '/api/monitoring'

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { Accept: 'application/json' }
  })
  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new Error(`Monitoring API request failed: ${response.status} ${response.statusText} ${body}`)
  }
  return response.json() as Promise<T>
}

export function fetchSnapshot(): Promise<MonitoringDashboardSnapshot> {
  return getJson<MonitoringDashboardSnapshot>('/snapshot')
}

export function fetchDataPoints(): Promise<MachineDataPoint[]> {
  return getJson<MachineDataPoint[]>('/data-points')
}

export function fetchServices(): Promise<MonitoringServiceDescriptor[]> {
  return getJson<MonitoringServiceDescriptor[]>('/services')
}

export function fetchAasProperties(): Promise<MonitoringAasProperty[]> {
  return getJson<MonitoringAasProperty[]>('/aas/properties')
}

export function fetchMappings(): Promise<MonitoringMappingDescriptor[]> {
  return getJson<MonitoringMappingDescriptor[]>('/mappings')
}

export function fetchEvents(limit = 100): Promise<MonitoringEventDescriptor[]> {
  return getJson<MonitoringEventDescriptor[]>(`/events?limit=${limit}`)
}
