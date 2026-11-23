package com.hengx.simulatedspawner;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 通用配置:保存在游戏目录下的 config/simulatedspawner-common.toml。
 * 涵盖"每次数量无上限"与"禁用刷怪粒子"两个功能开关。
 */
public final class SimCommonConfig {

    public static final ForgeConfigSpec SPEC;

    /** 移除神化"每次数量"(spawn_count)升级的上限(原上限 16)。 */
    public static final ForgeConfigSpec.BooleanValue REMOVE_SPAWN_COUNT_LIMIT;

    /** 禁用刷怪笼刷怪时的粒子效果(刷怪爆裂粒子与生物的出生动画粒子)。 */
    public static final ForgeConfigSpec.BooleanValue DISABLE_SPAWNER_PARTICLES;

    /** 钻石块升级最多可应用的次数(时运/海之眷顾等级上限)。 */
    public static final ForgeConfigSpec.IntValue DIAMOND_BLOCK_MAX_USES;

    /** 锁定附魔的装备在附魔台上可升级到的最高附魔等级。 */
    public static final ForgeConfigSpec.IntValue LOCKED_ENCHANT_MAX_LEVEL;

    /** 灌注附魔是否只判定最低要求(忽略配方的 max_requirements 上限)。 */
    public static final ForgeConfigSpec.BooleanValue INFUSION_IGNORE_MAX_REQUIREMENTS;

    /** 清道夫附魔:一击击杀是否必定额外掉落一次(双倍)。 */
    public static final ForgeConfigSpec.BooleanValue SCAVENGER_ONE_SHOT_DOUBLES;

    /** 创造模式下 shift 右键刷怪笼是否直接打开配置界面。 */
    public static final ForgeConfigSpec.BooleanValue CREATIVE_SPAWNER_CONFIG;

    /**
     * 刷怪笼黑名单:列在这里的实体,其刷怪蛋放进(神化)刷怪笼时不消耗、笼子也不改动。
     * 默认空;按实体注册名精确匹配,也可以直接写刷怪蛋的物品注册名。
     */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SPAWNER_BLACKLIST;

    /**
     * 诊断日志总开关。<b>默认 false</b>:模拟掉落/模拟钓鱼的"为什么没有产出"提示、
     * 血战/锁定附魔/清道夫/黑名单等提示、强化台"血战候选池"逐条输出全部静默,
     * 且关闭时调用点不拼字符串、不查玩家名。排查问题时才打开。
     */
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG;

    /** 空列表常量(配置默认值)。 */
    private static final List<String> EMPTY_BLACKLIST = List.of();

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");
        REMOVE_SPAWN_COUNT_LIMIT = builder
            .comment("Remove the upper limit (16) of the Apotheosis 'spawn count' spawner upgrade.",
                "When true, the spawn count can be increased indefinitely.",
                "When false, the vanilla Apotheosis cap of 16 applies.")
            .define("removeSpawnCountLimit", true);
        DISABLE_SPAWNER_PARTICLES = builder
            .comment("Disable the particle effects when a spawner spawns a mob",
                "(the burst particles around the spawner and the mob's own spawn animation particles).",
                "The ambient flame/smoke particles of a running spawner are not affected.")
            .define("disableSpawnerParticles", true);
        DIAMOND_BLOCK_MAX_USES = builder
            .comment("Maximum number of times the diamond block upgrade can be applied to a spawner.",
                "Each application gives +1 Looting to simulated kills and +1 Luck of the Sea to simulated fishing.")
            .defineInRange("diamondBlockMaxUses", 10, 0, 100);
        LOCKED_ENCHANT_MAX_LEVEL = builder
            .comment("Maximum enchantment level that a locked-enchantment item can reach on the enchanting table.",
                "A locked item (enchanted item + 1 iron ingot in an anvil) upgrades its existing enchantments",
                "instead of gaining new ones; each enchanting table use raises every enchantment by 1 level.")
            .defineInRange("lockedEnchantMaxLevel", 10, 1, 255);
        INFUSION_IGNORE_MAX_REQUIREMENTS = builder
            .comment("Apotheosis Infusion Enchanting: only the minimum requirements of the recipe are checked,",
                "the maximum requirements (max_requirements) are ignored.",
                "When true, a high-stat enchanting setup can still perform infusions.",
                "When false, the vanilla behaviour applies (stats must stay between the min and max requirements).")
            .define("infusionIgnoreMaxRequirements", true);
        SCAVENGER_ONE_SHOT_DOUBLES = builder
            .comment("Apotheosis 'Scavenger' enchantment: if the mob is killed from full health with a single hit,",
                "the loot table is rolled one extra time (guaranteed double drops).",
                "This is settled independently from the normal Scavenger chance, so both can trigger.",
                "When false, only the vanilla Scavenger chance applies.")
            .define("scavengerOneShotDoubles", true);
        CREATIVE_SPAWNER_CONFIG = builder
            .comment("Allow opening the spawner config screen by shift-right-clicking an Apotheosis spawner in creative mode.",
                "The screen edits every spawner stat directly (including this mod's upgrades) without consuming materials.",
                "Only creative-mode players can open and use it; the server validates this again on every click.")
            .define("creativeSpawnerConfig", true);
        SPAWNER_BLACKLIST = builder
            .comment("Spawner blacklist: entity registry names (or spawn egg item ids) that can never be put into a spawner.",
                "When a player right-clicks an Apotheosis spawner while holding a blacklisted mob's spawn egg,",
                "the interaction is cancelled before anything happens: the egg is NOT consumed (it stays in the",
                "player's hand) and the spawner is left completely unchanged (no mob change, no stat/energy change).",
                "Matching is by exact registry name, e.g. [\"minecraft:ravager\", \"minecraft:warden\"].",
                "This works on both the client and the server side of the right-click, and it does not affect",
                "non-blacklisted spawn eggs at all. Empty by default (nothing is blocked).")
            .defineListAllowEmpty(List.of("spawnerBlacklist"), () -> EMPTY_BLACKLIST, o -> o instanceof String);
        builder.pop();

        builder.comment("诊断日志。默认全部关闭:关闭时相关代码只做一次 volatile 布尔判断,不拼日志字符串、不查玩家名。",
                "只有排查问题时才打开(每类情况最多 64 行,同 key 只写一次)。").push("debug");
        DEBUG_LOG = builder
            .comment("模拟掉落/模拟钓鱼/血战/锁定附魔/清道夫/黑名单/强化台候选池的诊断日志。",
                "false(默认)= 完全静默;true = 打开诊断(排查为何没有产出、血战为何不在候选池时用)。")
            .define("debugLog", false);
        builder.pop();

        SPEC = builder.build();
    }

    private SimCommonConfig() {}

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
