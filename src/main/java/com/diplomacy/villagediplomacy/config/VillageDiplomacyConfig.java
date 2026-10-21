package com.diplomacy.villagediplomacy.config;

import com.diplomacy.villagediplomacy.diplomacy.DiplomacyBalance;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class VillageDiplomacyConfig {
    public static final Server SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;

    static {
        Pair<Server, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = pair.getLeft();
        SERVER_SPEC = pair.getRight();
    }

    private VillageDiplomacyConfig() {
    }

    public static final class Server {
        public final ForgeConfigSpec.IntValue specializationScanRadius;
        public final ForgeConfigSpec.IntValue specializationSampleStep;
        public final ForgeConfigSpec.DoubleValue secondarySpecializationScoreRatio;
        public final ForgeConfigSpec.DoubleValue minimumSecondarySpecializationScore;
        public final ForgeConfigSpec.IntValue mountainAltitude;
        public final ForgeConfigSpec.IntValue mountainRelief;
        public final ForgeConfigSpec.DoubleValue tradeSurplusShare;
        public final ForgeConfigSpec.DoubleValue secondaryProductionMultiplier;
        public final ForgeConfigSpec.IntValue warehouseCapacityDays;
        public final ForgeConfigSpec.IntValue productionCheckIntervalTicks;

        private Server(ForgeConfigSpec.Builder builder) {
            builder.comment("One-time settlement specialization analysis")
                    .push("specialization");

            specializationScanRadius = builder
                    .comment("Horizontal radius around the central bell to inspect")
                    .defineInRange("scanRadius", DiplomacyBalance.SPECIALIZATION_SCAN_RADIUS, 16, 128);
            specializationSampleStep = builder
                    .comment("Distance in blocks between terrain samples; smaller values are more precise but slower")
                    .defineInRange("sampleStep", 4, 2, 16);
            secondarySpecializationScoreRatio = builder
                    .comment("Second-best score must reach this fraction of the primary score")
                    .defineInRange("secondaryScoreRatio", 0.65D, 0.25D, 1.0D);
            minimumSecondarySpecializationScore = builder
                    .comment("Absolute minimum evidence required for a secondary specialization")
                    .defineInRange("minimumSecondaryScore", 0.30D, 0.0D, 1.0D);
            mountainAltitude = builder
                    .comment("Average surface height treated as strong mountain evidence")
                    .defineInRange("mountainAltitude", 100, 64, 320);
            mountainRelief = builder
                    .comment("Height difference across the scan treated as strong mountain evidence")
                    .defineInRange("mountainRelief", 32, 4, 192);

            builder.pop();

            builder.comment("Settlement resource production and virtual warehouse")
                    .push("production");

            tradeSurplusShare = builder
                    .comment("Share of gross production placed into the trade warehouse; the remainder is consumed internally")
                    .defineInRange("tradeSurplusShare", DiplomacyBalance.TRADE_SURPLUS_SHARE, 0.05D, 1.0D);
            secondaryProductionMultiplier = builder
                    .comment("Production multiplier applied to a secondary specialization")
                    .defineInRange("secondarySpecializationMultiplier",
                            DiplomacyBalance.SECONDARY_SPECIALIZATION_MULTIPLIER, 0.05D, 1.0D);
            warehouseCapacityDays = builder
                    .comment("Number of daily trade-surplus batches that fit in the virtual warehouse")
                    .defineInRange("warehouseCapacityDays", DiplomacyBalance.WAREHOUSE_CAPACITY_DAYS, 1, 30);
            productionCheckIntervalTicks = builder
                    .comment("How often the server checks settlement production; production itself remains day-based")
                    .defineInRange("checkIntervalTicks", 200, 20, 2_400);

            builder.pop();
        }
    }
}
