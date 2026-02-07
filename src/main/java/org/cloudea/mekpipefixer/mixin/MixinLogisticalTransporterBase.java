package org.cloudea.mekpipefixer.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.cloudea.mekpipefixer.MekaPipeFixerConfig;
import org.cloudea.mekpipefixer.controller.TransporterNetworkController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mekanism.common.content.network.InventoryNetwork;
import mekanism.common.content.network.transmitter.LogisticalTransporterBase;
import mekanism.common.content.transporter.TransporterStack;

/**
 * Core Mixin targeting {@link LogisticalTransporterBase#onUpdateServer()}.
 * <p>
 * Uses two injection points:
 * <ul>
 *   <li><b>HEAD</b> — blocks extraction when the network is at capacity.
 *       This is safe at HEAD because we explicitly want to prevent the pull.</li>
 *   <li><b>TAIL</b> — applies smart tick scaling <em>after</em> Mekanism has
 *       already attempted the pull. This avoids the deadlock where idle detection
 *       prevents pulls, which keeps the network idle forever.</li>
 * </ul>
 * The transit processing section is never affected — items in transit keep moving.
 */
@Mixin(value = LogisticalTransporterBase.class, remap = false)
public abstract class MixinLogisticalTransporterBase {

    @Shadow
    protected int delay;

    @Shadow
    @Final
    protected Int2ObjectMap<TransporterStack> transit;

    /** Stores the delay value at the start of the tick so TAIL can detect whether a pull was attempted. */
    @Unique
    private int mekapipefixer$delayAtHead;

    /**
     * HEAD — reconciles cached network data and blocks extraction when at capacity.
     * <p>
     * Only sets delay when {@code delay <= 0} (transporter about to pull).
     * Capacity blocking is safe at HEAD because we want to prevent wasteful
     * extraction + pathfinding when the network is already full.
     */
    @Inject(method = "onUpdateServer", at = @At("HEAD"))
    private void mekapipefixer$onUpdateServerHead(CallbackInfo ci) {
        mekapipefixer$delayAtHead = this.delay;

        InventoryNetwork network = mekapipefixer$getNetwork();
        if (network == null) {
            return;
        }

        long tick = mekapipefixer$getTick();
        TransporterNetworkController ctrl = TransporterNetworkController.get();

        // Ensure the cached transit/capacity data is fresh
        ctrl.reconcileIfNeeded(network, tick);

        // Only block extraction when the transporter is about to pull
        if (this.delay > 0) {
            return;
        }

        // --- Network Capacity Limit + Backpressure ---
        if (MekaPipeFixerConfig.IDLE_BALANCER_ENABLED.get()
                && ctrl.isAtCapacity(network, tick)) {
            this.delay = MekaPipeFixerConfig.MAX_COOLDOWN.get();
        }
    }

    /**
     * TAIL — applies smart tick scaling after Mekanism has processed the tick.
     * <p>
     * Only fires when a pull was actually attempted this tick ({@code delayAtHead <= 0}).
     * After the pull, if the transporter's local transit is still empty and the
     * network is idle, the delay is boosted so the next poll happens later.
     * This lets the pull happen first, avoiding the deadlock where idle detection
     * blocks pulls which keeps the network idle forever.
     */
    @Inject(method = "onUpdateServer", at = @At("TAIL"))
    private void mekapipefixer$onUpdateServerTail(CallbackInfo ci) {
        // Only act after a pull attempt (delay was <= 0 at HEAD)
        if (mekapipefixer$delayAtHead > 0) {
            return;
        }

        if (!MekaPipeFixerConfig.SMART_TICKS_ENABLED.get()) {
            return;
        }

        if (!this.transit.isEmpty()) {
            return;
        }

        InventoryNetwork network = mekapipefixer$getNetwork();
        if (network == null) {
            return;
        }

        long tick = mekapipefixer$getTick();
        TransporterNetworkController ctrl = TransporterNetworkController.get();

        if (ctrl.isIdle(network, tick)) {
            int idleDelay = MekaPipeFixerConfig.IDLE_MULTIPLIER.get() * 10;
            this.delay = Math.max(this.delay, idleDelay);
        }
    }

    // --- Helper accessors via cast ---

    @Unique
    private InventoryNetwork mekapipefixer$getNetwork() {
        return ((LogisticalTransporterBase) (Object) this).getTransmitterNetwork();
    }

    @Unique
    private long mekapipefixer$getTick() {
        var level = ((LogisticalTransporterBase) (Object) this).getLevel();
        return level != null ? level.getGameTime() : 0L;
    }
}
