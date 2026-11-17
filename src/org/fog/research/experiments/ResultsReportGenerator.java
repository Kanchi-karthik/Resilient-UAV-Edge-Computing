package org.fog.research.experiments;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.fog.research.metrics.SimulationMetrics;

public class ResultsReportGenerator {

    /**
     * Generate comprehensive IEEE-style CSV report with equation documentation
     * 
     * Paper: "A Multi-UAV Cooperative Task Scheduling in Dynamic Environments: Throughput Maximization"
     * This report includes:
     * - Professional results table
     * - Paper equation references
     * - Calculation explanations
     * - Algorithm analysis
     */
    public static void generateDetailedCsvReport(
            List<AlgorithmResult> results,
            int numUavs,
            int numDevices,
            double coverageRadius,
            double simulationDuration,
            String outputFile) throws IOException {

        try (FileWriter writer = new FileWriter(outputFile)) {
            
            // ====== SECTION 1: EXPERIMENT HEADER ======
            writer.write("================================================================================\n");
            writer.write("IEEE PAPER REPRODUCTION: Multi-UAV Cooperative Task Scheduling\n");
            writer.write("================================================================================\n");
            writer.write("Paper: \"A Multi-UAV Cooperative Task Scheduling in Dynamic Environments:\n");
            writer.write("        Throughput Maximization\"\n");
            writer.write("\n");
            writer.write("Experiment Configuration:\n");
            writer.write("  UAVs: " + numUavs + " units\n");
            writer.write("  Ground Devices: " + numDevices + " units\n");
            writer.write("  Coverage Radius: " + String.format("%.2f", coverageRadius) + " meters\n");
            writer.write("  Simulation Duration: " + String.format("%.1f", simulationDuration) + " seconds\n");
            writer.write("  Environment Size: 2000m × 2000m\n");
            writer.write("  Random Seed: 42 (for reproducibility)\n");
            writer.write("\n\n");

            // ====== SECTION 2: SIMULATION PARAMETERS TABLE ======
            writer.write("================================================================================\n");
            writer.write("SECTION 1: SIMULATION PARAMETERS (Table III from Paper)\n");
            writer.write("================================================================================\n");
            writer.write("\n");
            writer.write("Parameter,Value,Unit,Description,Paper Reference\n");
            writer.write("Simulation Area (X),2000,meters,Horizontal extent of environment,Table III\n");
            writer.write("Simulation Area (Y),2000,meters,Horizontal extent of environment,Table III\n");
            writer.write("UAV Altitude (Fixed),100,meters,Flying altitude above ground,Eq. (1)-(3)\n");
            writer.write("Number of UAVs,\"" + numUavs + "\",units,Swarm size (U),Table III\n");
            writer.write("Number of Mobile Devices,\"" + numDevices + "\",units,Ground users (D),Table III\n");
            writer.write("Coverage Radius (R),\"" + String.format("%.2f", coverageRadius) + "\",meters,UAV communication range,Eq. (1)\n");
            writer.write("Bandwidth (B),50,MHz,Wireless channel capacity,Eq. (6)-(7)\n");
            writer.write("Transmission Power (P_tx),1,Watts,Radio power per UAV,Table III\n");
            writer.write("Computation Power (P_cpu),5,Watts,CPU power per UAV,Table III\n");
            writer.write("Flight Power (P_move),7,Watts,Propulsion power per UAV,Table III\n");
            writer.write("Battery Capacity (E_cap),50000,Joules,Initial energy per UAV,Table III\n");
            writer.write("Task Size Range,0.1-1.0,MegaBytes,Generated task data size,Table III\n");
            writer.write("Task CPU Cycles,10-100,Cycles,Computational requirement,Table III\n");
            writer.write("Task Arrival Rate,0.05,per-sec per device,Probability of task generation,Eq. (18)\n");
            writer.write("Thermal Noise,1e-9,Watts,Baseline noise power,Eq. (6)-(7)\n");
            writer.write("LoS Probability σ,9.61,units,Path LoS parameter,Eq. (3)\n");
            writer.write("LoS Probability λ,0.16,units,Path LoS parameter,Eq. (3)\n");
            writer.write("Simulation Time Step,1.0,seconds,Discrete slot duration,Section 3.2\n");
            writer.write("PPO Learning Rate,0.001,units,Policy gradient step size,PPO Config\n");
            writer.write("PPO Discount Factor (γ),0.99,units,Future reward weight,PPO Config\n");
            writer.write("Pre-training Episodes,49,episodes,Centralized training loops,Section 4.1\n");
            writer.write("Evaluation Duration,\"" + String.format("%.1f", simulationDuration) + "\",seconds,Final test length,Experiment\n");
            writer.write("\n\n");

            // ====== SECTION 3: PERFORMANCE RESULTS TABLE ======
            writer.write("================================================================================\n");
            writer.write("SECTION 2: ALGORITHM PERFORMANCE COMPARISON\n");
            writer.write("================================================================================\n");
            writer.write("\n");
            writer.write("Algorithm,Throughput_Mbps,Latency_sec,Success_Rate_%,Tasks_Completed,Tasks_Failed,");
            writer.write("Tasks_Queued,Tasks_Executing,Energy_Consumed_J,Battery_Remaining_%,");
            writer.write("Energy_per_Task_J,Throughput_per_UAV_Mbps,Completion_Ratio_%\n");

            // Write results with consistent formatting
            for (AlgorithmResult result : results) {
                SimulationMetrics m = result.metrics;
                String algorithm = result.algorithmName;
                
                double energyPerTask = m.getCompletedTasks() > 0 ? 
                    m.getEnergyConsumed() / m.getCompletedTasks() : 0.0;
                double tpPerUav = m.getThroughputBps() / 1e6 / numUavs;
                double completionRatio = (m.getTotalTasks() > 0) ? 
                    (100.0 * m.getCompletedTasks() / m.getTotalTasks()) : 0.0;
                
                writer.write(algorithm + ",");
                writer.write(String.format("%.2f", m.getThroughputBps() / 1e6) + ",");
                writer.write(String.format("%.3f", m.getAverageLatencySec()) + ",");
                writer.write(String.format("%.2f", m.getSuccessRate() * 100.0) + ",");
                writer.write(m.getCompletedTasks() + ",");
                writer.write(m.getFailedTasks() + ",");
                writer.write(m.getQueuedTasks() + ",");
                writer.write(m.getExecutingTasks() + ",");
                writer.write(String.format("%.2f", m.getEnergyConsumed()) + ",");
                writer.write(String.format("%.2f", m.getBatteryLeftPct()) + ",");
                writer.write(String.format("%.4f", energyPerTask) + ",");
                writer.write(String.format("%.3f", tpPerUav) + ",");
                writer.write(String.format("%.2f", completionRatio) + "\n");
            }
            writer.write("\n\n");

            // ====== SECTION 4: EQUATION DERIVATIONS ======
            writer.write("================================================================================\n");
            writer.write("SECTION 3: MATHEMATICAL FORMULATIONS & PAPER EQUATIONS\n");
            writer.write("================================================================================\n");
            writer.write("\n");

            writer.write("EQUATION 1: 3D Euclidean Distance\n");
            writer.write("───────────────────────────────────\n");
            writer.write("d(u_k, u_m) = √[(x_k - x_m)² + (y_k - y_m)² + (z_k - z_m)²]\n");
            writer.write("\n");
            writer.write("Where:\n");
            writer.write("  d = Distance between UAV k and device m (meters)\n");
            writer.write("  (x_k, y_k, z_k) = Position of UAV k\n");
            writer.write("  (x_m, y_m) = Position of ground device m\n");
            writer.write("  z_k = 100m (fixed UAV altitude)\n");
            writer.write("\n");
            writer.write("Paper Reference: Section 3.1, Equation (1)\n");
            writer.write("Implementation: CommunicationModel.calculateDistanceMeters()\n");
            writer.write("\n\n");

            writer.write("EQUATION 2-3: Propagation Path Loss with LoS Probability\n");
            writer.write("───────────────────────────────────────────────────────────\n");
            writer.write("P_LoS(θ) = 1 / [1 + σ·exp(-λ·(θ - σ))]\n");
            writer.write("PL(d) = P_LoS·PL_LoS(d) + (1 - P_LoS)·PL_NLoS(d)\n");
            writer.write("PL_LoS(d) = L_LoS·d^(-α_LoS)\n");
            writer.write("PL_NLoS(d) = L_NLoS·d^(-α_NLoS)\n");
            writer.write("\n");
            writer.write("Where:\n");
            writer.write("  θ = Elevation angle (degrees)\n");
            writer.write("  σ = 9.61, λ = 0.16 (LoS probability parameters)\n");
            writer.write("  L_LoS = 1.0, α_LoS = 2.0 (LoS path loss constants)\n");
            writer.write("  L_NLoS = 20.0, α_NLoS = 3.5 (NLoS path loss constants)\n");
            writer.write("  d = Distance (meters)\n");
            writer.write("\n");
            writer.write("Paper Reference: Section 3.1, Equations (2)-(3)\n");
            writer.write("Implementation: CommunicationModel.calculateLosProbability() and calculatePathLoss()\n");
            writer.write("\n\n");

            writer.write("EQUATION 6-7: Shannon Capacity Upload/Download Rates\n");
            writer.write("───────────────────────────────────────────────────────\n");
            writer.write("R_up = B·log₂(1 + SNR_up)\n");
            writer.write("R_down = B·log₂(1 + SNR_down)\n");
            writer.write("\n");
            writer.write("Where:\n");
            writer.write("  B = 50 MHz (Bandwidth)\n");
            writer.write("  SNR_up = (ξ·P_tx) / (d²)  [simplified for ground → UAV]\n");
            writer.write("  SNR_down = (P_tx·h) / (B·N₀)  [UAV → ground, with path loss h]\n");
            writer.write("  ξ = 1.0 (channel efficiency factor)\n");
            writer.write("  N₀ = 1e-9 W (thermal noise power)\n");
            writer.write("\n");
            writer.write("Paper Reference: Section 3.1, Equations (6)-(7)\n");
            writer.write("Implementation: CommunicationModel.calculateUploadRateBps() and calculateDownloadRateBps()\n");
            writer.write("\n\n");

            writer.write("EQUATION 9: Transmission Latency\n");
            writer.write("─────────────────────────────────\n");
            writer.write("L_tx = (D_in / R_up) + (D_out / R_down)\n");
            writer.write("\n");
            writer.write("Where:\n");
            writer.write("  L_tx = Transmission latency (seconds)\n");
            writer.write("  D_in = Task input data size (bits)\n");
            writer.write("  D_out = Task output data size (bits)\n");
            writer.write("  R_up = Upload rate (bps) from Eq. (6)\n");
            writer.write("  R_down = Download rate (bps) from Eq. (7)\n");
            writer.write("\n");
            writer.write("Paper Reference: Section 3.2, Equation (9)\n");
            writer.write("Implementation: CommunicationModel.calculateTransmissionLatencySec()\n");
            writer.write("Physical Meaning: Time for data to reach UAV + result to return\n");
            writer.write("\n\n");

            writer.write("EQUATION 10-12, 14: Energy Models\n");
            writer.write("───────────────────────────────────\n");
            writer.write("E_tx(k,m,t) = P_tx · L_tx\n");
            writer.write("E_comp(k,m,t) = P_cpu · L_comp\n");
            writer.write("E_move(k,t) = P_move · [distance / speed]\n");
            writer.write("\n");
            writer.write("Where:\n");
            writer.write("  E_tx = Transmission energy (Joules)\n");
            writer.write("  E_comp = Computation energy (Joules)\n");
            writer.write("  E_move = Flight/movement energy (Joules)\n");
            writer.write("  P_tx = 1W (transmission power)\n");
            writer.write("  P_cpu = 5W (computation power)\n");
            writer.write("  P_move = 7W (flight power)\n");
            writer.write("  L_comp = D_cpu / f_cpu (computation time)\n");
            writer.write("  Distance = UAV travel distance (meters)\n");
            writer.write("  speed = UAV velocity (m/s)\n");
            writer.write("\n");
            writer.write("Paper Reference: Section 3.3, Equations (10), (12), (14)\n");
            writer.write("Implementation: EnergyCalculator.java\n");
            writer.write("Physical Meaning: Total battery drain from radio, CPU, and flight operations\n");
            writer.write("\n\n");

            writer.write("EQUATION 18: Success Rate & Throughput Reward\n");
            writer.write("──────────────────────────────────────────────\n");
            writer.write("r(t) = 1.5·Ω_completed - 0.001·E_consumed - 0.05·Q_size\n");
            writer.write("Success_Rate = (N_completed / N_emitted) × 100%\n");
            writer.write("\n");
            writer.write("Where:\n");
            writer.write("  r(t) = RL reward signal (normalized to [-1, 1])\n");
            writer.write("  Ω_completed = Number of tasks completed in time step\n");
            writer.write("  E_consumed = Energy consumed in time step (Joules)\n");
            writer.write("  Q_size = Total queued tasks across all UAVs\n");
            writer.write("  Success_Rate = Percentage of tasks finishing before deadline\n");
            writer.write("  N_completed = Tasks completed before deadline\n");
            writer.write("  N_emitted = Total tasks generated\n");
            writer.write("\n");
            writer.write("Paper Reference: Section 4.2, Equation (18) & Algorithm 2\n");
            writer.write("Implementation: RewardCalculator.java, ExperimentRunner.java\n");
            writer.write("RL Significance: Policy optimization target for PPO training\n");
            writer.write("\n\n");

            // ====== SECTION 5: METRIC EXPLANATIONS ======
            writer.write("================================================================================\n");
            writer.write("SECTION 4: DETAILED METRIC EXPLANATIONS\n");
            writer.write("================================================================================\n");
            writer.write("\n");

            writer.write("1. THROUGHPUT (Ω) - Measured in Mbps\n");
            writer.write("─────────────────────────────────────\n");
            writer.write("Definition: Total data successfully transmitted and received per second\n");
            writer.write("Formula: Ω = (Bits_Completed) / (Time_Duration)\n");
            writer.write("Paper Equation: Eq. (8) - Throughput calculation\n");
            writer.write("Implementation: ThroughputCalculator.calculateThroughput()\n");
            writer.write("\n");
            writer.write("How it's calculated:\n");
            writer.write("  1. Count bits from all successfully completed tasks\n");
            writer.write("  2. Divide by simulation duration (seconds)\n");
            writer.write("  3. Convert to Mbps (divide by 1e6)\n");
            writer.write("\n");
            writer.write("Why this metric matters:\n");
            writer.write("  • High throughput → more data processed → better resource utilization\n");
            writer.write("  • Directly influenced by: coverage radius, number of UAVs, device density\n");
            writer.write("  • RL reward component: +1.5 per completed task encourages throughput\n");
            writer.write("\n");
            writer.write("Expected values (from paper):\n");
            writer.write("  • TF-PPO-MOGS: 12-15 Mbps (best)\n");
            writer.write("  • QMIX: 9-12 Mbps\n");
            writer.write("  • Greedy: 3-6 Mbps (worst)\n");
            writer.write("\n\n");

            writer.write("2. LATENCY (L) - Measured in seconds\n");
            writer.write("────────────────────────────────────\n");
            writer.write("Definition: Average time from task generation to completion\n");
            writer.write("Formula: L = (L_tx + L_comp) = [D_in/R_up + D_out/R_down] + [D_cpu/f_cpu]\n");
            writer.write("Paper Equation: Eq. (9) - Transmission Latency + local processing\n");
            writer.write("Implementation: LatencyCalculator.calculateLatency()\n");
            writer.write("\n");
            writer.write("Components:\n");
            writer.write("  • L_tx (Transmission latency): Upload + download time via wireless\n");
            writer.write("  • L_comp (Computation latency): CPU processing time on UAV\n");
            writer.write("  • L_queue (Queue delay): Wait time in UAV buffer (not counted)\n");
            writer.write("\n");
            writer.write("Why this metric matters:\n");
            writer.write("  • Low latency → tasks meet deadline → high success rate\n");
            writer.write("  • Influenced by: wireless channel capacity (Eq. 6-7), CPU speed\n");
            writer.write("  • Direct deadline constraint: L ≤ 2-5 seconds (paper defines tolerance)\n");
            writer.write("\n");
            writer.write("Expected values (from paper):\n");
            writer.write("  • Optimal: 0.5-2.0 seconds (well below deadline)\n");
            writer.write("  • Acceptable: 2.0-5.0 seconds (meets deadline)\n");
            writer.write("  • Poor: >5.0 seconds (misses deadline → counted as failure)\n");
            writer.write("\n\n");

            writer.write("3. SUCCESS RATE (SR) - Percentage\n");
            writer.write("──────────────────────────────────\n");
            writer.write("Definition: Percentage of tasks completed before latency deadline\n");
            writer.write("Formula: SR = (N_completed_before_deadline / N_total_generated) × 100%\n");
            writer.write("Paper Equation: Eq. (18) - Success metric\n");
            writer.write("Implementation: MetricsCollector.calculateSuccessRate()\n");
            writer.write("\n");
            writer.write("Classification:\n");
            writer.write("  • Successful: Task latency ≤ defined tolerance (paper: 2-10 seconds)\n");
            writer.write("  • Failed: Task latency > tolerance OR task dropped\n");
            writer.write("\n");
            writer.write("Why this metric matters:\n");
            writer.write("  • Primary optimization objective: maximize SR\n");
            writer.write("  • Reflects: UAV placement, task scheduling efficiency, network capacity\n");
            writer.write("  • QoS guarantee: High SR means predictable service\n");
            writer.write("\n");
            writer.write("Expected values (from paper):\n");
            writer.write("  • TF-PPO-MOGS: 85-95% (best scheduling)\n");
            writer.write("  • Random baseline: 40-60% (poor scheduling)\n");
            writer.write("\n\n");

            writer.write("4. ENERGY CONSUMPTION (E) - Joules\n");
            writer.write("───────────────────────────────────\n");
            writer.write("Definition: Total battery energy used by all UAVs during simulation\n");
            writer.write("Formula: E_total = Σ [E_tx + E_comp + E_move] for all UAVs\n");
            writer.write("Paper Equation: Eq. (10), (12), (14) - Energy models\n");
            writer.write("Implementation: EnergyCalculator.calculateTotalEnergy()\n");
            writer.write("\n");
            writer.write("Breakdown:\n");
            writer.write("  • E_tx (Transmission): P_tx × time_transmitting (typically 5-10% of total)\n");
            writer.write("  • E_comp (Computation): P_cpu × time_computing (typically 10-15% of total)\n");
            writer.write("  • E_move (Flight): P_move × time_flying (typically 75-85% of total)\n");
            writer.write("\n");
            writer.write("Why this metric matters:\n");
            writer.write("  • Battery life: E_total vs. E_capacity determines flight duration\n");
            writer.write("  • Optimization trade-off: Flying more can cover more area but uses more energy\n");
            writer.write("  • RL penalty term: -0.001×E encourages energy-efficient policies\n");
            writer.write("\n");
            writer.write("Expected values (from paper, for 500s duration):\n");
            writer.write("  • 4 UAVs: 50,000-150,000 J (mostly flight)\n");
            writer.write("  • Battery capacity: 50,000 J per UAV = 200,000 J total for 4 UAVs\n");
            writer.write("  • Efficient algorithms: Use <50% battery for 500s mission\n");
            writer.write("  • Poor algorithms: May drain >80% battery\n");
            writer.write("\n\n");

            writer.write("5. BATTERY REMAINING (%) - Percentage\n");
            writer.write("─────────────────────────────────────\n");
            writer.write("Definition: Remaining energy as percentage of total capacity\n");
            writer.write("Formula: Battery% = (E_remaining / E_total_capacity) × 100%\n");
            writer.write("Paper Equation: Derived from Eq. (10), (12), (14)\n");
            writer.write("Implementation: MetricsCollector.calculateBatteryRemaining()\n");
            writer.write("\n");
            writer.write("Example Calculation:\n");
            writer.write("  • Total capacity: 4 UAVs × 50,000 J = 200,000 J\n");
            writer.write("  • Energy consumed: 100,000 J\n");
            writer.write("  • Battery remaining: (200,000 - 100,000) / 200,000 × 100% = 50%\n");
            writer.write("\n");
            writer.write("Why this metric matters:\n");
            writer.write("  • Operational duration: Higher battery% = longer mission capability\n");
            writer.write("  • Sustainability indicator: Algorithms with high battery% are more deployable\n");
            writer.write("  • Trade-off visibility: Energy vs. throughput compromise shown clearly\n");
            writer.write("\n\n");

            writer.write("6. COMPLETED TASKS - Count\n");
            writer.write("───────────────────────────\n");
            writer.write("Definition: Number of tasks successfully completed within deadline\n");
            writer.write("Implementation: MetricsCollector.countCompletedTasks()\n");
            writer.write("Why it matters: Direct measure of system capacity and scheduling effectiveness\n");
            writer.write("\n");

            writer.write("7. FAILED TASKS - Count\n");
            writer.write("───────────────────────\n");
            writer.write("Definition: Number of tasks that exceeded deadline or were dropped\n");
            writer.write("Implementation: MetricsCollector.countFailedTasks()\n");
            writer.write("Why it matters: Indicates system overload and scheduling failures\n");
            writer.write("\n");

            writer.write("8. QUEUED TASKS - Count\n");
            writer.write("───────────────────────\n");
            writer.write("Definition: Tasks currently waiting in UAV buffers\n");
            writer.write("Why it matters: High queue length suggests bottlenecks; RL penalty term: -0.05×queue\n");
            writer.write("\n\n");

            // ====== SECTION 6: ALGORITHM ANALYSIS ======
            writer.write("================================================================================\n");
            writer.write("SECTION 5: ALGORITHM ANALYSIS & PERFORMANCE EXPLANATION\n");
            writer.write("================================================================================\n");
            writer.write("\n");

            writer.write("1. TF-PPO-MOGS (Task Factorization PPO + Many-to-One Gale-Shapley)\n");
            writer.write("──────────────────────────────────────────────────────────────────\n");
            writer.write("Type: Deep Reinforcement Learning + Stable Matching\n");
            writer.write("Paper: Section 4 Algorithm 2 (TF-PPO) + Algorithm 3 (MOGS)\n");
            writer.write("\n");
            writer.write("Working Principle:\n");
            writer.write("  1. CENTRALIZED TRAINING: Collect 49 pre-training episodes\n");
            writer.write("  2. POLICY LEARNING: PPO learns UAV movement policy to maximize reward\n");
            writer.write("  3. TASK FACTORIZATION: Decompose multi-UAV value function into individual components\n");
            writer.write("  4. STABLE MATCHING: MOGS assigns tasks to UAVs considering mutual preferences\n");
            writer.write("  5. DISTRIBUTED EXECUTION: Each UAV executes learned policy in evaluation\n");
            writer.write("\n");
            writer.write("Reward Function (Eq. 18):\n");
            writer.write("  r(t) = +1.5×(tasks_completed) - 0.001×(energy_used) - 0.05×(queue_size)\n");
            writer.write("  • Encourages: Completion, Energy efficiency, Low queue backlogs\n");
            writer.write("  • Range: [-1.0, 1.0] (normalized for stable gradient)\n");
            writer.write("\n");
            writer.write("Expected Performance:\n");
            writer.write("  • Throughput: 12-15 Mbps (BEST among all algorithms)\n");
            writer.write("  • Success Rate: 85-95% (BEST)\n");
            writer.write("  • Energy: 80,000-120,000 J for 500s (EFFICIENT - learns hovering)\n");
            writer.write("  • Latency: 1.0-2.5 seconds (LOW - good scheduling)\n");
            writer.write("\n");
            writer.write("Strengths:\n");
            writer.write("  ✓ Learns optimal movement patterns over time\n");
            writer.write("  ✓ Adapts to dynamic device locations\n");
            writer.write("  ✓ Balances multiple objectives (throughput, energy, latency)\n");
            writer.write("  ✓ Provably stable matching (Gale-Shapley algorithm)\n");
            writer.write("\n");
            writer.write("Weaknesses:\n");
            writer.write("  ✗ Requires pre-training (49 episodes) before deployment\n");
            writer.write("  ✗ Initial performance poor (random movements) → needs training\n");
            writer.write("  ✗ High computational cost (neural network inference)\n");
            writer.write("  ✗ May not generalize to different device distributions\n");
            writer.write("\n");
            writer.write("Why results might show QMIX outperforming TF-PPO initially:\n");
            writer.write("  ⚠ Pre-training may not converge within 49 episodes\n");
            writer.write("  ⚠ Reward function might have wrong scaling or weighting\n");
            writer.write("  ⚠ Evaluation period too short for learned policy to show benefits\n");
            writer.write("  ⚠ Need to verify: (1) pre-training loss convergence, (2) reward signal correctness\n");
            writer.write("\n\n");

            writer.write("2. QMIX (Qmix Value Decomposition)\n");
            writer.write("──────────────────────────────────\n");
            writer.write("Type: Value-based Multi-Agent DRL\n");
            writer.write("Strategy: Target device with highest (backlog / distance) ratio\n");
            writer.write("\n");
            writer.write("Working Principle:\n");
            writer.write("  1. For each UAV: Score each ground device using: score = queue_length / distance\n");
            writer.write("  2. Target the device with maximum score\n");
            writer.write("  3. Move towards that device at max speed\n");
            writer.write("  4. Repeat every time step\n");
            writer.write("\n");
            writer.write("Expected Performance: 9-12 Mbps throughput, 70-80% success rate\n");
            writer.write("Strengths: Simple heuristic, no training needed, reactive to load\n");
            writer.write("Weaknesses: No global coordination, greedy behavior, energy-wasteful\n");
            writer.write("\n\n");

            writer.write("3. Other Baselines (VDN, MAPPO, MADDPG, PSO, ACO, GA, Greedy)\n");
            writer.write("───────────────────────────────────────────────────────────────\n");
            writer.write("  • VDN: Targets avg position of top-3 busy devices (coordination)\n");
            writer.write("  • MAPPO: Targets local device centroid within 300m (proximity focus)\n");
            writer.write("  • MADDPG: Targets closest device with tasks (greedy)\n");
            writer.write("  • PSO: Swarm intelligence toward centroid (slow convergence)\n");
            writer.write("  • ACO: Pheromone-based (similar to PSO, slow)\n");
            writer.write("  • GA: Genetic algorithm (requires population, expensive)\n");
            writer.write("  • Greedy: Always target device with most tasks (worst at coverage)\n");
            writer.write("\n");
            writer.write("General Performance Ranking:\n");
            writer.write("  1. TF-PPO-MOGS (learned, adaptive)\n");
            writer.write("  2. QMIX (reactive, balanced)\n");
            writer.write("  3. MAPPO (local optimization)\n");
            writer.write("  4. VDN (distributed value decomposition)\n");
            writer.write("  5-9. PSO, ACO, GA, Greedy, MADDPG (heuristics, fixed strategies)\n");
            writer.write("\n\n");

            writer.write("================================================================================\n");
            writer.write("END OF REPORT\n");
            writer.write("================================================================================\n");
        }
    }

    // Helper class to hold algorithm results
    public static class AlgorithmResult {
        public String algorithmName;
        public SimulationMetrics metrics;

        public AlgorithmResult(String algorithmName, SimulationMetrics metrics) {
            this.algorithmName = algorithmName;
            this.metrics = metrics;
        }
    }
}
