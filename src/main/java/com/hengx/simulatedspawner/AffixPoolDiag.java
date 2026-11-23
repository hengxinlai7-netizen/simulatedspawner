package com.hengx.simulatedspawner;

import java.util.List;
import java.util.Set;

import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixRegistry;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixType;
import dev.shadowsoffire.apotheosis.adventure.loot.LootCategory;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 临时诊断:在强化台点"刷新"时,把「血战」为什么不在候选池里说清楚。
 * <p>
 * 神化的刷新候选池 = {@code LootController.getAvailableAffixes(物品, 该槽位稀有度, 已有词条, 该槽位词条的 AffixType)},
 * 也就是「同 AffixType + canApplyTo(物品, 类别, 稀有度) 为真 + 尚未存在」。
 * 这里把每一步的结果逐条打出来,这样一眼就能看出卡在哪一环。
 * <p>
 * AffixType 是枚举(STAT/POTION/ABILITY/ANCIENT/SOCKET/DURABILITY),由词条的 Java 类决定,不由 JSON 控制。
 * <p>
 * 1.7.9 起<b>默认关闭</b>({@code general.debugLog=false}):关闭时第一行就返回,
 * 不读物品、不查词条注册表、不拼任何字符串。排查"血战为什么不在候选池"时才打开。
 */
public final class AffixPoolDiag {

    /** 血战词条的注册 id。 */
    public static final ResourceLocation BLOOD_WAR = new ResourceLocation("simulatedspawner", "armor/blood_war");

    /** 同一个物品连续刷新时只详细输出一次,避免刷屏。 */
    private static String lastKey = "";

    private AffixPoolDiag() {}

    public static void onReroll(ItemStack item) {
        if (!SimDebug.enabled()) {
            return;
        }
        if (item.isEmpty()) {
            return;
        }
        try {
            run(item);
        }
        catch (Throwable t) {
            SimulatedSpawner.LOGGER.warn("[诊断-血战] 诊断本身出错: {}", t.toString());
        }
    }

    private static void run(ItemStack item) {
        List<AffixInstance> affixes = AffixToggle.itemAffixes(item);
        String key = item.getItem() + "|" + affixes.size() + "|" + item.getDamageValue();
        if (key.equals(lastKey)) {
            return;
        }
        lastKey = key;

        LootCategory cat = LootCategory.forItem(item);
        Affix bloodWar = AffixRegistry.INSTANCE.getValue(BLOOD_WAR);
        AffixType bwType = bloodWar == null ? null : bloodWar.getType();

        SimulatedSpawner.LOGGER.info("[诊断-血战] 物品={} 神化类别={} 已有词条={} 条|血战已注册={} 血战类型={}",
            item.getItem(), cat.getName(), affixes.size(), bloodWar != null ? "是" : "否",
            bwType == null ? "-" : bwType.name());

        if (bloodWar == null) {
            SimulatedSpawner.LOGGER.warn("[诊断-血战] 血战词条未注册,请把上面「Registered N affixes」那行一起发我");
            return;
        }

        Set<DynamicHolder<? extends Affix>> existing = AffixHelper.getAffixesImpl(item).keySet();
        int index = 0;
        for (AffixInstance inst : affixes) {
            index++;
            DynamicHolder<? extends Affix> holder = inst.affix();
            Affix affix = holder.get();
            if (affix == null) {
                continue;
            }
            AffixType type = affix.getType();
            LootRarity rarity = inst.rarity().get();
            List<DynamicHolder<? extends Affix>> pool = LootController.getAvailableAffixes(item, rarity, existing, type);
            boolean has = false;
            StringBuilder sample = new StringBuilder();
            for (int i = 0; i < pool.size(); i++) {
                Affix cand = pool.get(i).get();
                if (cand == null) {
                    continue;
                }
                ResourceLocation cid = cand.getId();
                if (BLOOD_WAR.equals(cid)) {
                    has = true;
                }
                if (i < 6) {
                    sample.append(i > 0 ? "," : "").append(cid.getPath());
                }
            }
            SimulatedSpawner.LOGGER.info("[诊断-血战] 槽位{}: 词条={} 类型={} 稀有度={} → 候选池={} 个 含血战={} 示例=[{}]",
                index, affix.getId(), type.name(), holder.getId() == null ? "?" : inst.rarity().getId(),
                pool.size(), has ? "是" : "否", sample);
            if (!has) {
                boolean typeOk = type == bwType;
                SimulatedSpawner.LOGGER.info("[诊断-血战]   原因排查: 类型一致={} 血战对[{} / {}]可应用={}",
                    typeOk, cat.getName(), inst.rarity().getId(),
                    bloodWar.canApplyTo(item, cat, rarity) ? "是" : "否");
            }
        }
    }
}
