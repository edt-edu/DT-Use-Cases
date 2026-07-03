# DT Monitoring UI

Vue/Vite frontend for monitoring the Digital Twin machine state and runtime data.

## Run

Start the Spring Boot backend first. The frontend expects the monitoring API under `/api/monitoring` and proxies `/api` to `http://localhost:8080` during development.

```bash
npm install
npm run dev
```

Open <http://localhost:5173>.

## Backend endpoints used

- `GET /api/monitoring/snapshot`
- `GET /api/monitoring/data-points`
- `GET /api/monitoring/services`
- `GET /api/monitoring/aas/properties`

For a different backend URL during development:

```bash
VITE_DT_BACKEND_URL=http://localhost:8081 npm run dev
```
