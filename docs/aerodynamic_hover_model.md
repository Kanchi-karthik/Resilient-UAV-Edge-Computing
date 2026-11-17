# Aerodynamic Hover Physics and Energy Depletion Formulation

This document details the analytical momentum-theory hover power equations and the quantitative assessment of idle hover energy waste caused by task fragmentation.

---

## 1. Classical Momentum Aerodynamics for Rotary-Wing Flight

Unlike terrestrial edge servers whose power dissipation is predominantly digital/computational, the power consumption of an aerial edge node is overwhelmingly dominated by aerodynamic propulsion required to maintain steady hover over a disaster theatre.

Based on actuator disk principles and classical momentum theory (Zeng et al., *IEEE TWC*, 2019):
$$P_{\text{hover}} = P_0 + (1 + k) P_i$$

Where:
- $P_0$ is the blade profile drag power:
  $$P_0 = \frac{\delta}{8} \rho_{\text{air}} s A_{\text{total}} \Omega^3 r_{\text{rotor}}^3$$
- $k$ is the incremental correction factor for rotor tip losses.
- $P_i$ is the induced aerodynamic power required to produce hovering thrust:
  $$P_i = \sqrt{\frac{W^3}{2 \rho_{\text{air}} A_{\text{total}}}}$$

---

## 2. Step-by-Step Numerical Calibration for Quadrotor Platform

For a representative reconnaissance quadrotor configured with:
- Gross Takeoff Mass: $m_{\text{total}} = 2.0\,\text{kg}$
- Number of Rotors: $n_{\text{rotors}} = 4$
- Rotor Radius: $r_{\text{rotor}} = 0.20\,\text{m}$
- Air Density at Sea Level: $\rho_{\text{air}} = 1.225\,\text{kg}\cdot\text{m}^{-3}$
- Profile Drag Power: $P_0 = 79.86\,\text{W}$
- Tip Loss Correction Factor: $k = 0.13$

The step-by-step physical parameters evaluate as follows:

1. **Total Weight:**
   $$W = m_{\text{total}} \cdot g = 2.0\,\text{kg} \times 9.81\,\text{m}\cdot\text{s}^{-2} = 19.62\,\text{N}$$

2. **Total Rotor Disk Area:**
   $$A_{\text{total}} = n_{\text{rotors}} \cdot \pi r_{\text{rotor}}^2 = 4 \times \pi (0.20\,\text{m})^2 = 0.5028\,\text{m}^2$$

3. **Induced Hover Power:**
   $$P_i = \sqrt{\frac{(19.62\,\text{N})^3}{2 \times 1.225\,\text{kg}\cdot\text{m}^{-3} \times 0.5028\,\text{m}^2}} = \sqrt{\frac{7552.4}{1.2319}} = 78.30\,\text{W}$$

4. **Total Propulsion Hover Power:**
   $$P_{\text{hover}} = 79.86\,\text{W} + (1 + 0.13) \times 78.30\,\text{W} = 79.86\,\text{W} + 88.48\,\text{W} = 168.34\,\text{W} \approx 168.0\,\text{W}$$

---

## 3. Quantification of Idle Hover Waste from Task Fragmentation

When sequential stages of a multi-stage workflow are assigned to physically disjoint UAVs, intermediate feature data ($d_{1,2}$) must traverse the Air-to-Air (A2A) wireless mesh link.

1. **Wireless Transfer Latency ($R_{\text{A2A}} = 15\,\text{Mbps}$):**
   $$t_{\text{transfer}} = \frac{d_{1,2}}{R_{\text{A2A}}} = \frac{8.0 \times 10^6\,\text{bits}}{15.0 \times 10^6\,\text{bps}} \approx 0.533\,\text{s}$$

2. **Wasted Idle Hover Energy per Fragmented Pipeline:**
   $$E_{\text{wasted}} = P_{\text{hover}} \times t_{\text{transfer}} = 168.0\,\text{W} \times 0.533\,\text{s} \approx 89.6\,\text{J}$$

3. **Cumulative Swarm Waste across 100 Workflows:**
   $$E_{\text{total\_waste}} = 100 \times 89.6\,\text{J} \approx 8\,960\,\text{J} \approx 8.96\,\text{kJ}$$

This idle waiting expenditure represents approximately **$4.5\%$** of the entire $200\,\text{kJ}$ battery capacity of the quadrotor, completely squandered without executing a single instruction. Chain-Aware CBBA enforces pipeline co-location, reducing $t_{\text{transfer}} \rightarrow 0$ and saving $89.6\,\text{J}$ per workflow.
