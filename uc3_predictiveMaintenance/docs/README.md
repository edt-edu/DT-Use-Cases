# DT Engine Documentation

This folder contains the working documentation for the Kotlin/Spring Boot Digital Twin engine.

Recommended reading order:

1. [Architecture Overview](architecture.md)
2. [Runbook](runbook.md)
3. [MQTT Gateway](mqtt-gateway.md)
4. [MAPE-K Conveyor KPI Service](mapek-conveyor-kpi.md)
5. [Mappings and Synchronization](mappings-and-synchronization.md)
6. [Monitoring REST API](monitoring-api.md)
7. [Troubleshooting](troubleshooting.md)

The current implementation is intentionally modular. MQTT, SQLite, AAS, services, mappings, synchronization, and monitoring are wired through Spring configuration so that parts can be replaced later.
