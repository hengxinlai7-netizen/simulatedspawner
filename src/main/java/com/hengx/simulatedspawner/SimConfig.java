package com.hengx.simulatedspawner;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 服务端配置:保存在存档目录下的 serverconfig/simulatedspawner-server.toml。
 */
public final class SimConfig {

    public static final ForgeConfigSpec SPEC;

    /** 向上搜索容器的最大格数(1=只检测刷怪笼正上方的方块)。 */
    public static final ForgeConfigSpec.IntValue CONTAINER_SEARCH_RANGE;

    /** 模拟击杀时假玩家携带的抢夺等级(0-3)。 */
    public static final ForgeConfigSpec.IntValue SIMULATED_LOOTING;

    /** 模拟击杀时的幸运值。 */
    public static final ForgeConfigSpec.DoubleValue SIMULATED_LUCK;

    /** 捕捉附魔附加掉蛋的阈值百分比:目标最大生命值 <= 捕捉等级 × 该百分比 × 击杀者最大生命值 时必掉刷怪蛋。 */
    public static final ForgeConfigSpec.DoubleValue CAPTURING_BONUS_PERCENT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");
        CONTAINER_SEARCH_RANGE = builder
            .comment("Max distance (in blocks) to search upwards for a container (1 = only the block directly above the spawner).",
                "If no block with an item inventory is found in range, the spawner stops running.")
            .defineInRange("containerSearchRange", 1, 1, 32);
        SIMULATED_LOOTING = builder
            .comment("Looting level of the simulated player kill (0-3).",
                "Affects loot functions based on the Looting enchantment (e.g. wither skeleton skulls).")
            .defineInRange("simulatedLooting", 0, 0, 3);
        SIMULATED_LUCK = builder
            .comment("Luck value of the simulated player kill. Affects loot tables with luck conditions.")
            .defineInRange("simulatedLuck", 0.0D, 0.0D, 10.0D);
        CAPTURING_BONUS_PERCENT = builder
            .comment("Bonus egg drop threshold for the Apotheosis Capturing enchantment:",
                "a mob always drops its spawn egg when its max health is no greater than",
                "capturingLevel x thisPercent x the killer's own max health.",
                "Example: 0.3 with Capturing III = mobs up to 90% of the killer's max health always drop an egg.",
                "This drop is a fixed single egg and is not affected by Looting.")
            .defineInRange("capturingBonusPercent", 0.30D, 0.0D, 10.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private SimConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
    }
}
