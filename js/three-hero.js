/**
 * Interactive 3D UAV Aerial Edge Computing Simulation Canvas
 * Displays quadrotor UAV nodes hovering over a 3D terrain grid,
 * Air-to-Ground ingestion beams from ground IoT nodes,
 * and peer-to-peer Air-to-Air wireless mesh consensus pulses.
 */

(function () {
  const canvas = document.getElementById('uav3dCanvas');
  if (!canvas) return;

  const ctx = canvas.getContext('2d');
  let width, height;
  let animationFrameId;

  // Camera angles
  let rotX = 0.45;
  let rotY = 0.6;
  let isDragging = false;
  let lastMouseX = 0;
  let lastMouseY = 0;

  // UAV Nodes
  const numUAVs = 6;
  const uavs = [];
  const groundNodes = [];
  const packets = [];

  function resize() {
    width = canvas.parentElement.clientWidth;
    height = canvas.parentElement.clientHeight;
    canvas.width = width * window.devicePixelRatio;
    canvas.height = height * window.devicePixelRatio;
    ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
  }

  function initSimulation() {
    uavs.length = 0;
    groundNodes.length = 0;
    packets.length = 0;

    // Initialize 6 UAVs in a collaborative mesh formation
    const radius = 140;
    for (let i = 0; i < numUAVs; i++) {
      const angle = (i / numUAVs) * Math.PI * 2;
      uavs.push({
        id: i + 1,
        baseX: Math.cos(angle) * radius,
        baseY: -80 - (i % 2) * 25, // Altitude in 3D (Y up)
        baseZ: Math.sin(angle) * radius,
        x: 0, y: 0, z: 0,
        battery: 184.2 - i * 4.5, // kJ
        hoverPower: 168.0,        // Watts
        propAngle: 0,
        phase: i * 1.1,
        tasks: 3 + (i % 3)
      });
    }

    // Initialize ground IoT sensor clusters
    for (let i = 0; i < 18; i++) {
      groundNodes.push({
        x: (Math.random() - 0.5) * 360,
        y: 60, // Ground level
        z: (Math.random() - 0.5) * 360,
        type: i % 3 === 0 ? 'Thermal' : (i % 3 === 1 ? 'Seismic' : 'Gas'),
        pulse: Math.random() * Math.PI
      });
    }
  }

  // 3D to 2D projection
  function project(x, y, z) {
    // Rotation Y
    const cosY = Math.cos(rotY);
    const sinY = Math.sin(rotY);
    const x1 = x * cosY - z * sinY;
    const z1 = z * cosY + x * sinY;

    // Rotation X
    const cosX = Math.cos(rotX);
    const sinX = Math.sin(rotX);
    const y2 = y * cosX - z1 * sinX;
    const z2 = z1 * cosX + y * sinX;

    const fov = 420;
    const distance = 460;
    const scale = fov / (distance + z2);

    return {
      x: width / 2 + x1 * scale,
      y: height / 2 + y2 * scale,
      scale: scale,
      depth: z2
    };
  }

  function drawGrid() {
    ctx.strokeStyle = 'rgba(56, 189, 248, 0.12)';
    ctx.lineWidth = 1;

    const gridSize = 200;
    const step = 40;
    const groundY = 60;

    ctx.beginPath();
    for (let x = -gridSize; x <= gridSize; x += step) {
      const p1 = project(x, groundY, -gridSize);
      const p2 = project(x, groundY, gridSize);
      ctx.moveTo(p1.x, p1.y);
      ctx.lineTo(p2.x, p2.y);
    }
    for (let z = -gridSize; z <= gridSize; z += step) {
      const p1 = project(-gridSize, groundY, z);
      const p2 = project(gridSize, groundY, z);
      ctx.moveTo(p1.x, p1.y);
      ctx.lineTo(p2.x, p2.y);
    }
    ctx.stroke();
  }

  function drawUAV(uav, time) {
    // Natural hovering oscillation
    uav.x = uav.baseX + Math.sin(time * 1.5 + uav.phase) * 6;
    uav.y = uav.baseY + Math.cos(time * 2.0 + uav.phase) * 4;
    uav.z = uav.baseZ + Math.cos(time * 1.2 + uav.phase) * 6;
    uav.propAngle += 0.45;

    const center = project(uav.x, uav.y, uav.z);
    if (center.scale <= 0) return;

    const s = center.scale * 16;

    // Draw shadow on ground
    const shadow = project(uav.x, 60, uav.z);
    ctx.fillStyle = 'rgba(15, 23, 42, 0.25)';
    ctx.beginPath();
    ctx.ellipse(shadow.x, shadow.y, s * 1.2, s * 0.6, 0, 0, Math.PI * 2);
    ctx.fill();

    // Altitude indicator dashed line
    ctx.strokeStyle = 'rgba(56, 189, 248, 0.25)';
    ctx.setLineDash([3, 3]);
    ctx.beginPath();
    ctx.moveTo(center.x, center.y);
    ctx.lineTo(shadow.x, shadow.y);
    ctx.stroke();
    ctx.setLineDash([]);

    // Quadrotor frame arms (X config)
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(center.x - s, center.y - s * 0.4);
    ctx.lineTo(center.x + s, center.y + s * 0.4);
    ctx.moveTo(center.x - s, center.y + s * 0.4);
    ctx.lineTo(center.x + s, center.y - s * 0.4);
    ctx.stroke();

    // Central avionics fuselage
    ctx.fillStyle = '#dc2626'; // Red fuselage
    ctx.beginPath();
    ctx.arc(center.x, center.y, s * 0.45, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    // 4 spinning rotors
    const rotorOffsets = [
      [-s, -s * 0.4],
      [s, s * 0.4],
      [-s, s * 0.4],
      [s, -s * 0.4]
    ];

    ctx.strokeStyle = 'rgba(56, 189, 248, 0.8)';
    ctx.lineWidth = 1.5;
    rotorOffsets.forEach(([ox, oy]) => {
      ctx.beginPath();
      ctx.ellipse(center.x + ox, center.y + oy, s * 0.55, s * 0.25, uav.propAngle, 0, Math.PI * 2);
      ctx.stroke();
    });

    // Node label badge
    ctx.fillStyle = '#ffffff';
    ctx.font = `600 ${Math.max(10, Math.floor(10 * center.scale))}px monospace`;
    ctx.fillText(`UAV-${uav.id}`, center.x + s + 4, center.y - 2);
  }

  function drawConnections() {
    // Air-to-Air DSRC mesh links between neighboring UAVs
    ctx.lineWidth = 1.2;
    for (let i = 0; i < uavs.length; i++) {
      for (let j = i + 1; j < uavs.length; j++) {
        const u1 = uavs[i];
        const u2 = uavs[j];
        const p1 = project(u1.x, u1.y, u1.z);
        const p2 = project(u2.x, u2.y, u2.z);

        const dist = Math.hypot(u1.x - u2.x, u1.y - u2.y, u1.z - u2.z);
        if (dist < 260) {
          ctx.strokeStyle = 'rgba(239, 68, 68, 0.4)';
          ctx.beginPath();
          ctx.moveTo(p1.x, p1.y);
          ctx.lineTo(p2.x, p2.y);
          ctx.stroke();
        }
      }
    }
  }

  function drawGroundNodes(time) {
    groundNodes.forEach((gn, idx) => {
      gn.pulse += 0.04;
      const p = project(gn.x, gn.y, gn.z);
      if (p.scale <= 0) return;

      const size = Math.max(2, 4 * p.scale);

      // Sensor node pulse
      const pulseSize = size * (1.5 + Math.sin(gn.pulse) * 0.8);
      ctx.fillStyle = 'rgba(245, 158, 11, 0.25)';
      ctx.beginPath();
      ctx.arc(p.x, p.y, pulseSize, 0, Math.PI * 2);
      ctx.fill();

      // Core sensor point
      ctx.fillStyle = '#f59e0b';
      ctx.beginPath();
      ctx.arc(p.x, p.y, size, 0, Math.PI * 2);
      ctx.fill();

      // Periodic Air-to-Ground ingestion beam to nearest UAV
      if (idx % 3 === 0 && Math.sin(gn.pulse) > 0.85) {
        const targetUav = uavs[idx % uavs.length];
        const up = project(targetUav.x, targetUav.y, targetUav.z);
        ctx.strokeStyle = 'rgba(251, 191, 36, 0.4)';
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(p.x, p.y);
        ctx.lineTo(up.x, up.y);
        ctx.stroke();
      }
    });
  }

  function render(timeMs) {
    const time = timeMs * 0.001;
    ctx.clearRect(0, 0, width, height);

    // Dynamic rotation if user is not dragging
    if (!isDragging) {
      rotY += 0.003;
    }

    drawGrid();
    drawGroundNodes(time);
    drawConnections();
    uavs.forEach(u => drawUAV(u, time));

    // Update Telemetry HUD every 60 frames
    if (Math.floor(timeMs / 100) % 5 === 0) {
      const batEl = document.getElementById('hudBattery');
      const altEl = document.getElementById('hudAlt');
      const latencyEl = document.getElementById('hudLatency');
      if (batEl) batEl.textContent = `${(184.2 - (time * 0.08) % 15).toFixed(1)} kJ`;
      if (altEl) altEl.textContent = `${(85.4 + Math.sin(time) * 2.1).toFixed(1)} m`;
      if (latencyEl) latencyEl.textContent = '1.06 s';
    }

    animationFrameId = requestAnimationFrame(render);
  }

  // Mouse Interaction for rotating 3D view
  canvas.addEventListener('mousedown', (e) => {
    isDragging = true;
    lastMouseX = e.clientX;
    lastMouseY = e.clientY;
  });

  window.addEventListener('mouseup', () => {
    isDragging = false;
  });

  canvas.addEventListener('mousemove', (e) => {
    if (!isDragging) return;
    const dx = e.clientX - lastMouseX;
    const dy = e.clientY - lastMouseY;
    rotY += dx * 0.008;
    rotX = Math.max(0.1, Math.min(1.2, rotX - dy * 0.008));
    lastMouseX = e.clientX;
    lastMouseY = e.clientY;
  });

  // Touch controls for mobile
  canvas.addEventListener('touchstart', (e) => {
    if (e.touches.length === 1) {
      isDragging = true;
      lastMouseX = e.touches[0].clientX;
      lastMouseY = e.touches[0].clientY;
    }
  });

  window.addEventListener('touchend', () => {
    isDragging = false;
  });

  canvas.addEventListener('touchmove', (e) => {
    if (!isDragging || e.touches.length !== 1) return;
    const dx = e.touches[0].clientX - lastMouseX;
    const dy = e.touches[0].clientY - lastMouseY;
    rotY += dx * 0.01;
    rotX = Math.max(0.1, Math.min(1.2, rotX - dy * 0.01));
    lastMouseX = e.touches[0].clientX;
    lastMouseY = e.touches[0].clientY;
  });

  window.addEventListener('resize', () => {
    resize();
  });

  // Start simulation
  resize();
  initSimulation();
  animationFrameId = requestAnimationFrame(render);
})();
