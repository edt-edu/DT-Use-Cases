# Digital Twin Use Cases

This repository contains implementations for the digital twin use cases from the paper `Digital Twins for Manufacturing Systems: A Case Study Based On a Fischertechnik Factory`.

The paper describes three use cases:

- **UC1: Monitoring for maximum energy consumption** measures machine-level
  energy use, aggregates it for the production process, compares it with a
  dynamic limit from a smart energy grid, and shows the result in a dashboard
  with notifications. The implementation is available at [`./uc1_monitoring`](./uc1_monitoring).
- **UC2: Flexible replanning** tracks the execution state of the production
  line, detects when the expected path is blocked by a component failure, records
  the disruption, and selects an alternative path where possible. The implementation is available at [`./uc2_replanning`](./uc2_replanning).
- **UC3: Predictive maintenance** records operational signals such as motor
  speed and cycle timing, compares them with expected behavior, and uses the
  results to support maintenance planning. The implementation is available at [`./uc3_predictiveMaintenance`](./uc3_predictiveMaintenance).
