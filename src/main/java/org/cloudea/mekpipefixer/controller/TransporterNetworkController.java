package org.cloudea.mekpipefixer.controller;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.cloudea.mekpipefixer.MekaPipeFixerConfig;
import org.cloudea.mekpipefixer.util.TierCapacity;

import mekanism.common.content.network.InventoryNetwork;
import mekanism.common.content.network.transmitter.LogisticalTransporterBase;

/**
 * Central controller for pressure-aware transporter optimization.
 * <p>
 * Maintains per-network cached data (items in transit, capacity) using a
 * {@link WeakHashMap} so entries are automatically cleaned up when networks
 * are destroyed (merge/split). All state is lazily computed and periodically
 * reconciled against the actual transit maps.
 * <p>
 * Thread-safety: all access is expected from the server thread only (Minecraft
 * server tick). The synchronized wrapper is a safety net for edge cases.
 */
public final class TransporterNetworkController {

    private static final TransporterNetworkController INSTANCE = new TransporterNetworkController();

    private final Map<InventoryNetwork, NetworkData> cache =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * @return the global singleton controller instance
     */
    public static TransporterNetworkController get() {
        return INSTANCE;
    }

    /**
     * Checks whether the given network has reached its virtual capacity.
     * <p>
     * Relies on the cached transit count which is refreshed every
     * {@code reconcile_interval} ticks. Between refreshes the cached
     * value is used, which may be slightly stale — this is intentional
     * to keep the per-tick cost near zero.
     *
     * @param network the transporter network to check
     * @param tick    current server game time
     * @return {@code true} if items in transit &ge; computed capacity
     */
    public boolean isAtCapacity(InventoryNetwork network, long tick) {
        if (!MekaPipeFixerConfig.CAPACITY_ENABLED.get()) {
            return false;
        }
        NetworkData data = getOrCreate(network);
        refreshIfNeeded(data, network, tick);
        return data.transitCount >= data.capacity;
    }

    /**
     * Returns {@code true} when the network has zero items in transit
     * (based on cached count).
     *
     * @param network the transporter network
     * @param tick    current server game time
     * @return {@code true} if the network is idle
     */
    public boolean isIdle(InventoryNetwork network, long tick) {
        NetworkData data = getOrCreate(network);
        refreshIfNeeded(data, network, tick);
        return data.transitCount == 0;
    }

    /**
     * Forces a cache refresh if the reconcile interval has elapsed.
     * Called from the Mixin injection; only the first transporter to tick
     * in each interval triggers the recount — subsequent calls in the
     * same interval are no-ops.
     *
     * @param network the network to reconcile
     * @param tick    current server game time
     */
    public void reconcileIfNeeded(InventoryNetwork network, long tick) {
        NetworkData data = getOrCreate(network);
        refreshIfNeeded(data, network, tick);
    }

    /**
     * Clears all cached network data. Called on server start/stop.
     */
    public void clear() {
        cache.clear();
    }

    // ---- internals ----

    private NetworkData getOrCreate(InventoryNetwork network) {
        return cache.computeIfAbsent(network, k -> new NetworkData());
    }

    private void refreshIfNeeded(NetworkData data, InventoryNetwork network, long tick) {
        int interval = MekaPipeFixerConfig.RECONCILE_INTERVAL.get();
        if (tick - data.lastRefreshTick < interval) {
            return;
        }
        data.lastRefreshTick = tick;
        data.transitCount = countTransitItems(network);
        data.capacity = calculateCapacity(network);
    }

    private static int countTransitItems(InventoryNetwork network) {
        int total = 0;
        for (LogisticalTransporterBase transporter : network.getTransmitters()) {
            total += transporter.getTransit().size();
        }
        return total;
    }

    private static int calculateCapacity(InventoryNetwork network) {
        int total = 0;
        for (LogisticalTransporterBase transporter : network.getTransmitters()) {
            total += TierCapacity.get(transporter.tier);
        }
        return Math.max(1, total);
    }

    /**
     * Cached per-network state. Entries are automatically evicted via
     * {@link WeakHashMap} when the network object is garbage-collected.
     */
    private static final class NetworkData {
        int transitCount;
        int capacity;
        long lastRefreshTick = Long.MIN_VALUE;
    }

    private TransporterNetworkController() {}
}
