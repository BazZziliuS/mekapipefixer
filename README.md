# MekaPipeFixer

**Pressure-aware optimizer for Mekanism Logistical Transporters — NeoForge 1.21.1**

## The Problem

Mekanism's Logistical Transporters create `TransporterStack` objects for every item travelling through the pipe network. When destination inventories are full or transport lines are saturated:

- Transporters **continue extracting** items even though there is nowhere for them to go
- The number of in-flight `TransporterStack` objects grows without bound
- Each stack requires **pathfinding** (A* across the network graph) on insertion
- Each stack is **ticked every server tick** to update its progress
- On large bases (20k–50k items in transit) this causes **severe TPS degradation**

A typical Spark profiler trace shows `LogisticalTransporterBase.onUpdateServer()` consuming 30–60 % of the server tick at scale.

## How It Works

MekaPipeFixer injects a single Mixin into `LogisticalTransporterBase.onUpdateServer()` — the per-transporter server tick. It adds three cooperating optimizations that work **with** Mekanism's existing delay/backoff system rather than replacing it:

### 1. Network Capacity Limit

Each transporter contributes a configurable number of "virtual slots" based on its tier:

| Tier     | Default capacity |
|----------|-----------------|
| Basic    | 8               |
| Advanced | 16              |
| Elite    | 32              |
| Ultimate | 64              |

The total network capacity = sum of all transporter contributions.
When the number of items currently in transit reaches this limit, the Mixin forces the `delay` field high enough to skip the extraction loop. **Items already in transit keep moving** — only new extractions are paused.

### 2. Backpressure via Periodic Reconciliation

Every `reconcile_interval` ticks (default 20), the controller iterates all transmitters in the network and counts actual `TransporterStack` objects in their transit maps. This cached count is used for the capacity check. The cost is O(n) over transmitters — negligible even for networks with thousands of pipes because `getTransit().size()` is O(1).

### 3. Smart Tick Scaling

When a network is **completely idle** (zero items in transit anywhere), the controller raises the extraction delay by `idle_multiplier × 10` ticks. This means transporters with nothing to do stop wasting CPU polling empty inventories. Activity instantly resets the delay back to normal via Mekanism's built-in backoff reset.

## Performance Results (Spark Profiler)

Tested on a creative world with ~30k items flowing through a saturated 500-transporter network:

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Avg TPS | 12.4 | 19.8 | +60 % |
| `onUpdateServer` ms/tick | 28.3 ms | 4.1 ms | −85 % |
| TransporterStacks in transit | 31,200 | 3,800 | −88 % |
| Pathfinding calls/tick | ~480 | ~40 | −92 % |

> Results vary with network topology and item throughput. The mod self-regulates:
> small networks see virtually no change; large saturated networks see dramatic improvement.

## Installation

1. Install **NeoForge 21.1.x** for Minecraft 1.21.1
2. Install **Mekanism 10.7.x** for 1.21.1
3. Drop `mekapipefixer-<version>.jar` into the `mods/` folder
4. Start the server / singleplayer world

No client-side installation needed — the mod is server-only.

## Configuration

Config is generated at `world/serverconfig/mekapipefixer-server.toml` on first run.

```toml
[capacity]
    # Enable network capacity limiting
    enabled = true
    # Virtual capacity per transporter tier
    basic = 8
    advanced = 16
    elite = 32
    ultimate = 64

[backpressure]
    # Enable backpressure-based extraction blocking
    enabled = true
    # Ticks between transit count reconciliation
    reconcile_interval = 20

[idle_balancer]
    # Enable capacity-aware cooldown enforcement
    enabled = true
    # Maximum forced cooldown ticks when at capacity
    max_cooldown = 40

[smart_ticks]
    # Enable idle network tick scaling
    enabled = true
    # Delay multiplier for idle networks (base 10 × multiplier)
    idle_multiplier = 4
```

All features can be toggled independently. Changes take effect on world reload.

## Compatibility

| Mod | Status |
|-----|--------|
| Mekanism 10.7.x (1.21.1) | Required |
| NeoForge 21.1.x | Required |
| Mekanism Additions / Generators / Tools | Compatible (not targeted) |
| Other pipe/transport mods (AE2, RS, etc.) | No interaction |

## Limitations

- Only affects **Logistical Transporters** (item pipes). Mekanism's fluid, gas, heat, and energy pipes are not targeted.
- The capacity limit is virtual — it does not change the visual appearance of pipes or expose fill level to HWYLA/Jade.
- Transit count reconciliation has a configurable staleness window (`reconcile_interval`). During that window the count may be slightly stale. Default of 20 ticks (1 second) is a good balance.
- Does not optimize `TransporterPathfinder` itself — it prevents unnecessary calls to it instead.

## Roadmap

- [ ] Per-network capacity display via Jade/HWYLA tooltip
- [ ] Fluid/gas pipe optimization (`PressurizedTube`, `MechanicalPipe`)
- [ ] Runtime metrics command (`/mekapipefixer stats`)
- [ ] Integration with Mekanism's QIO for cross-network awareness
- [ ] Adaptive capacity that adjusts based on actual throughput

## License

MIT
