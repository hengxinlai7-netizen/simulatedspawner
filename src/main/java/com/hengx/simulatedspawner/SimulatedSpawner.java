package com.hengx.simulatedspawner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.shadowsoffire.apotheosis.spawn.modifiers.SpawnerStats;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 神化(Apotheosis)刷怪笼 · 模拟掉落扩展
 * <p>
 * 手持钻石剑右键神化刷怪笼,即可把刷怪笼切换为"模拟掉落"模式:
 * <ul>
 * <li>刷怪笼不再生成任何生物;</li>
 * <li>每次"刷怪"改为按该生物类型掷一次"玩家击杀"掉落表(支持抢夺/幸运配置);</li>
 * <li>掉落物全部塞入刷怪笼上方的容器;</li>
 * <li>上方没有容器,或容器放不下本次全部掉落时,刷怪笼停止运行并等待下一轮重试;</li>
 * <li>速度、数量、范围、红石控制、无视条件/光照/玩家等神化自带升级全部照常生效。</li>
 * </ul>
 * 副手持石英再右击一次即可关闭该模式。
 * <p>
 * 另附"无法破坏"附魔:1 本书 + 8 个黑曜石无序合成附魔书,铁砧附给任何工具/武器后,
 * 直接给物品添加无法破坏(Unbreakable)标签,并自动清除附魔本身。
 */
@Mod(SimulatedSpawner.MODID)
public class SimulatedSpawner {

    public static final String MODID = "simulatedspawner";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MODID);

    /** "无法破坏"附魔 */
    public static final RegistryObject<Enchantment> UNBREAKABLE = ENCHANTMENTS.register("unbreakable", UnbreakableEnchantment::new);

    /** 自定义合成配方序列化器(捕捉附魔书:书+4蛋 / 捕捉书+4蛋升级) */
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);

    public static final RegistryObject<RecipeSerializer<CapturingRecipe>> CAPTURING_SERIALIZER =
        RECIPE_SERIALIZERS.register("capturing", CapturingRecipe.Serializer::new);

    /** 清道夫附魔书配方:1 本书 + 8 个肉类(标签 simulatedspawner:meats)。 */
    public static final RegistryObject<RecipeSerializer<ScavengerBookRecipe>> SCAVENGER_SERIALIZER =
        RECIPE_SERIALIZERS.register("scavenger_book", ScavengerBookRecipe.Serializer::new);

    /** 菜单类型:创造模式 shift 右键刷怪笼打开的配置界面。 */
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    public static final RegistryObject<MenuType<SpawnerConfigMenu>> SPAWNER_CONFIG_MENU = MENUS.register("spawner_config",
        () -> IForgeMenuType.create((windowId, playerInventory, buf) -> new SpawnerConfigMenu(windowId, playerInventory, buf)));

    public SimulatedSpawner() {
        SimConfig.register();
        SimCommonConfig.register();
        SimNetwork.register();

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // 诊断日志的 volatile 快照:配置加载/热重载时刷新(默认关闭)。
        modBus.addListener(SimDebug::onConfigLoad);
        modBus.addListener(SimDebug::onConfigReload);
        ENCHANTMENTS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        MENUS.register(modBus);
        MinecraftForge.EVENT_BUS.register(new UnbreakableTagHandler());
        MinecraftForge.EVENT_BUS.register(new CapturingBonusHandler());
        MinecraftForge.EVENT_BUS.register(new EnchantLockHandler());
        MinecraftForge.EVENT_BUS.register(new ScavengerBonusHandler());
        MinecraftForge.EVENT_BUS.register(new SpawnerBlacklistHandler());

        // 注册自定义刷怪笼 stat:神化升级配方 JSON 中通过 id 引用它,
        // 应用升级时把"模拟掉落"/"模拟钓鱼"标记、增益等级、信标标记与启示之证标记写入刷怪笼方块实体。
        SpawnerStats.REGISTRY.put(SimulatedStat.ID, SimulatedStat.INSTANCE);
        SpawnerStats.REGISTRY.put(FishingStat.ID, FishingStat.INSTANCE);
        SpawnerStats.REGISTRY.put(BonusLevelStat.ID, BonusLevelStat.INSTANCE);
        SpawnerStats.REGISTRY.put(BeaconStat.ID, BeaconStat.INSTANCE);
        SpawnerStats.REGISTRY.put(RevelationStat.ID, RevelationStat.INSTANCE);
    }
}
