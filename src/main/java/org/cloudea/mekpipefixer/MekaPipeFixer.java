package org.cloudea.mekpipefixer;

import org.cloudea.mekpipefixer.controller.TransporterNetworkController;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * MekaPipeFixer — pressure-aware optimizer for Mekanism Logistical Transporters.
 * <p>
 * Injects into Mekanism via Mixin to add network capacity limits, backpressure
 * tracking, idle balancing, and smart tick scaling. Reduces TPS impact of large
 * item transport networks without altering gameplay behaviour.
 */
@Mod(MekaPipeFixer.MODID)
public class MekaPipeFixer {

    public static final String MODID = "mekapipefixer";
    public static final Logger LOG = LogUtils.getLogger();

    public MekaPipeFixer(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, MekaPipeFixerConfig.SPEC);
        NeoForge.EVENT_BUS.register(this);
        LOG.info("MekaPipeFixer loaded — pressure-aware transporter optimization active");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        TransporterNetworkController.get().clear();
        LOG.info("MekaPipeFixer controller initialized");
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        TransporterNetworkController.get().clear();
        LOG.info("MekaPipeFixer controller cleared");
    }
}
