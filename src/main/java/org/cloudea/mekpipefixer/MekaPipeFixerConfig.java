package org.cloudea.mekpipefixer;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration for MekaPipeFixer.
 * <p>
 * Controls all optimization features: network capacity limits,
 * backpressure tracking, idle balancer, and smart tick scaling.
 */
public final class MekaPipeFixerConfig {

    public static final ModConfigSpec SPEC;

    // --- Capacity ---
    public static final ModConfigSpec.BooleanValue CAPACITY_ENABLED;
    public static final ModConfigSpec.IntValue CAPACITY_BASIC;
    public static final ModConfigSpec.IntValue CAPACITY_ADVANCED;
    public static final ModConfigSpec.IntValue CAPACITY_ELITE;
    public static final ModConfigSpec.IntValue CAPACITY_ULTIMATE;

    // --- Backpressure ---
    public static final ModConfigSpec.BooleanValue BACKPRESSURE_ENABLED;
    public static final ModConfigSpec.IntValue RECONCILE_INTERVAL;

    // --- Idle Balancer ---
    public static final ModConfigSpec.BooleanValue IDLE_BALANCER_ENABLED;
    public static final ModConfigSpec.IntValue MAX_COOLDOWN;

    // --- Smart Ticks ---
    public static final ModConfigSpec.BooleanValue SMART_TICKS_ENABLED;
    public static final ModConfigSpec.IntValue IDLE_MULTIPLIER;

    static {
        var builder = new ModConfigSpec.Builder();

        builder.comment("Network capacity limits.",
                        "Each transporter adds tierCapacity slots to the network.",
                        "When items in transit >= total capacity, extraction is paused.")
                .push("capacity");
        CAPACITY_ENABLED = builder
                .comment("Enable network capacity limiting")
                .define("enabled", true);
        CAPACITY_BASIC = builder
                .comment("Virtual capacity per Basic Logistical Transporter")
                .defineInRange("basic", 8, 1, 256);
        CAPACITY_ADVANCED = builder
                .comment("Virtual capacity per Advanced Logistical Transporter")
                .defineInRange("advanced", 16, 1, 256);
        CAPACITY_ELITE = builder
                .comment("Virtual capacity per Elite Logistical Transporter")
                .defineInRange("elite", 32, 1, 256);
        CAPACITY_ULTIMATE = builder
                .comment("Virtual capacity per Ultimate Logistical Transporter")
                .defineInRange("ultimate", 64, 1, 256);
        builder.pop();

        builder.comment("Backpressure tracking.",
                        "Periodically counts actual items in transit across the network",
                        "to enforce the capacity limit accurately.")
                .push("backpressure");
        BACKPRESSURE_ENABLED = builder
                .comment("Enable backpressure-based extraction blocking")
                .define("enabled", true);
        RECONCILE_INTERVAL = builder
                .comment("Ticks between transit count reconciliation (lower = more accurate, higher = cheaper)")
                .defineInRange("reconcile_interval", 20, 1, 200);
        builder.pop();

        builder.comment("Idle balancer.",
                        "Forces a cooldown on transporters when the network is at capacity,",
                        "preventing wasted CPU on extraction attempts that will be blocked.")
                .push("idle_balancer");
        IDLE_BALANCER_ENABLED = builder
                .comment("Enable capacity-aware cooldown enforcement")
                .define("enabled", true);
        MAX_COOLDOWN = builder
                .comment("Maximum forced cooldown ticks when network is at capacity")
                .defineInRange("max_cooldown", 40, 5, 200);
        builder.pop();

        builder.comment("Smart tick scaling.",
                        "Increases pull delay for idle networks (no items in transit)",
                        "so transporters with nothing to do tick less frequently.")
                .push("smart_ticks");
        SMART_TICKS_ENABLED = builder
                .comment("Enable idle network tick scaling")
                .define("enabled", true);
        IDLE_MULTIPLIER = builder
                .comment("Delay multiplier for idle networks (base delay 10 * multiplier)")
                .defineInRange("idle_multiplier", 4, 1, 20);
        builder.pop();

        SPEC = builder.build();
    }

    private MekaPipeFixerConfig() {}
}
