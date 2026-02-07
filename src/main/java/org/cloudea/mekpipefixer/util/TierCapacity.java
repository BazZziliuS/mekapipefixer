package org.cloudea.mekpipefixer.util;

import org.cloudea.mekpipefixer.MekaPipeFixerConfig;

import mekanism.common.tier.TransporterTier;

/**
 * Maps {@link TransporterTier} to the configured virtual capacity per transporter.
 */
public final class TierCapacity {

    /**
     * Returns the configured capacity contribution for a single transporter of the given tier.
     *
     * @param tier the transporter tier
     * @return capacity slots contributed by one transporter of this tier
     */
    public static int get(TransporterTier tier) {
        return switch (tier) {
            case BASIC -> MekaPipeFixerConfig.CAPACITY_BASIC.get();
            case ADVANCED -> MekaPipeFixerConfig.CAPACITY_ADVANCED.get();
            case ELITE -> MekaPipeFixerConfig.CAPACITY_ELITE.get();
            case ULTIMATE -> MekaPipeFixerConfig.CAPACITY_ULTIMATE.get();
        };
    }

    private TierCapacity() {}
}
