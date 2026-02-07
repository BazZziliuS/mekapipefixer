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
 * Injects at the HEAD of the server tick to manipulate the existing {@code delay}
 * field. When the network is at capacity or idle, the delay is raised so that
 * Mekanism's own pull-section guard ({@code if (delay > 0)}) naturally skips
 * extraction attempts. The transit processing section that follows the pull
 * section is unaffected — items already in transit continue moving normally.
 * <p>
 * This approach avoids cancelling the entire tick and works with Mekanism's
 * built-in exponential backoff rather than fighting it.
 */
@Mixin(value = LogisticalTransporterBase.class, remap = false)
public abstract class MixinLogisticalTransporterBase {

    @Shadow
    protected int delay;

    @Shadow
    @Final
    protected Int2ObjectMap<TransporterStack> transit;

    /**
     * Injected before the body of {@code onUpdateServer()}.
     * <ul>
     *   <li><b>Capacity limit + Backpressure:</b> if the network's cached transit count
     *       meets or exceeds its computed capacity, {@code delay} is forced to at least
     *       {@code max_cooldown} ticks. This prevents the pull loop from running and
     *       avoids wasteful extraction/pathfinding work.</li>
     *   <li><b>Smart tick scaling:</b> if the network is idle (zero items in transit)
     *       <em>and</em> this transporter's local transit map is empty, {@code delay}
     *       is raised by {@code idle_multiplier * 10} ticks so the pull check runs
     *       far less often.</li>
     * </ul>
     */
    @Inject(method = "onUpdateServer", at = @At("HEAD"))
    private void mekapipefixer$onUpdateServerHead(CallbackInfo ci) {
        InventoryNetwork network = mekapipefixer$getNetwork();
        if (network == null) {
            return;
        }

        long tick = mekapipefixer$getTick();
        TransporterNetworkController ctrl = TransporterNetworkController.get();

        // Ensure the cached transit/capacity data is fresh
        ctrl.reconcileIfNeeded(network, tick);

        // --- Network Capacity Limit + Backpressure ---
        if (MekaPipeFixerConfig.IDLE_BALANCER_ENABLED.get()
                && ctrl.isAtCapacity(network, tick)) {
            int cooldown = MekaPipeFixerConfig.MAX_COOLDOWN.get();
            this.delay = Math.max(this.delay, cooldown);
        }

        // --- Smart Tick Scaling ---
        if (MekaPipeFixerConfig.SMART_TICKS_ENABLED.get()
                && this.transit.isEmpty()
                && ctrl.isIdle(network, tick)) {
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
