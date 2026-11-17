# Simulation Parameters & Environment Calibration

This document consolidates all physical parameters, channel models, and hardware specifications configured in the extended `iFogSim2` discrete-event simulation harness.

---

## 1. Network & Physical Environment Parameters

| Parameter | Symbol | Configured Value | Literature / Standard Source |
| :--- | :---: | :---: | :--- |
| Operational Area | $A$ | $2000 \times 2000\,\text{m}^2$ | Standard disaster grid |
| Ground IoT Devices | $M$ | 200 to 800 devices | Dispersed field sensors |
| UAV Edge Swarm Size | $N$ | 10 quadrotors | Autonomous aerial edge tier |
| Flight Altitude | $z$ | $50\,\text{m}$ to $100\,\text{m}$ | Urban LoS corridor |
| Carrier Frequency (A2G) | $f_c$ | $2.4\,\text{GHz}$ | LTE / 5G Sidelink |
| Carrier Frequency (A2A) | $f_{\text{A2A}}$ | $5.9\,\text{GHz}$ | IEEE 802.11p DSRC |
| A2G Channel Bandwidth | $B_{\text{A2G}}$ | $10\,\text{MHz}$ | Cellular ingestion link |
| A2A Nominal Throughput | $R_{\text{A2A}}$ | $15\,\text{Mbps}$ | Peer-to-peer aerial mesh |
| Noise Power Spectral Density | $\sigma^2$ | $-174\,\text{dBm/Hz}$ | Standard thermal noise |

---

## 2. Onboard Computing & Battery Hardware

| Parameter | Symbol | Configured Value | Description |
| :--- | :---: | :---: | :--- |
| Processor Speed | $f_i$ | $3\,000\,\text{MIPS}$ | Embedded multi-core ARM SoC |
| Battery Capacity | $E_{\text{battery}}$ | $200\,\text{kJ}$ | 4S LiPo battery pack |
| Mandatory Reserve Floor | $E_{\text{reserve}}$ | $20\,\text{kJ}$ | Safe landing energy buffer |
| Steady Hover Power | $P_{\text{hover}}$ | $168.0\,\text{W}$ | Calibrated momentum model |
| Full CPU Power Draw | $P_{\text{cpu}}$ | $12.5\,\text{W}$ | Active CMOS computing load |
| Base Static Power | $P_{\text{static}}$ | $1.5\,\text{W}$ | Idle digital leakage |
| Transceiver Power | $P_{\text{comm}}$ | $1.2\,\text{W}$ | Active wireless transmission |

---

## 3. DAG Computational Workload Specifications

Each emergency mission task arrives as a 3-stage Directed Acyclic Graph (DAG):
1. **Stage 1 (Raw Ingestion & Framing):**
   - Workload Size: $L_1 = 1\,000\,\text{MI}$
   - Data Payload: $d_1 = 0.5\text{--}1.0\,\text{MB}$
2. **Stage 2 (Spatial Filtering & Gradient Extraction):**
   - Workload Size: $L_2 = 2\,500\,\text{MI}$
   - Intermediate Payload: $d_2 = 0.2\text{--}0.4\,\text{MB}$
3. **Stage 3 (CNN Feature Classification & Localization):**
   - Workload Size: $L_3 = 4\,000\,\text{MI}$
   - Output Payload: $d_3 = 0.05\,\text{MB}$

**End-to-End Pipeline Deadline:** $T_j^{\max} \in [1.2, 2.5]\,\text{s}$
