# MRDCS — Marine Research Distributed Communication System

A distributed system built in **Java using MPI (MPJ Express)** that simulates real-time marine sensor ingestion, anomaly detection, broadcast dissemination, and fault-tolerant storage for oceanographic research workflows.

---

## 🔹 Overview

MRDCS models a real-world marine monitoring network where ocean sensors continuously stream data to a distributed backend.  
The system processes incoming telemetry in parallel, detects anomalies (e.g., pollution spikes), routes critical data to cloud storage, broadcasts alerts to research nodes, and redundantly stores normal readings for fault tolerance.

This project demonstrates **distributed systems concepts** using **message passing (MPI)** rather than shared memory or traditional web frameworks.

---


### Node Responsibilities
- **Monitoring Node**: Generates and streams sensor data at fixed intervals
- **Processing Node**: Performs anomaly detection and routes data accordingly
- **Cloud Storage Node**: Stores critical data and broadcasts alerts
- **Research Node**: Consumes broadcasted anomalies for scientific analysis
- **Backup Storage Node**: Persists non-anomalous data for redundancy

---

## 🔹 Technologies Used

- Java
- MPI (MPJ Express)
- Parallel message passing (Send / Receive / Broadcast)
- Distributed system design
- Fault-tolerant data routing

---

## 🔹 Features

- Multi-process distributed execution (5 MPI ranks)
- Real-time sensor data simulation
- Anomaly detection and conditional routing
- Cloud-style broadcast to research nodes
- Redundant backup storage
- Structured logging per node

---

## 🔹 Setup (Windows)

### Prerequisites:
- Java JDK 17+
- MPJ Express installed at `C:\mpj`

### Compile
```bash
javac -cp ".;C:\mpj\lib\mpj.jar" -d build src/MRDCS.java
