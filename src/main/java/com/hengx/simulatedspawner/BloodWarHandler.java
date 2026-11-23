package com.hengx.simulatedspawner;

import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 护甲词缀「血战」的机制部分:
 * <ul>
 * <li>生命上限 +20%(独立乘区 = {@code MULTIPLY_TOTAL})由神化内置属性词缀承担,
 *     见 {@code data/simulatedspawner/affixes/armor/blood_war.json},各稀有度同值且步进为 0 —— 天生不可升级;</li>
 * <li>本类负责另外两段:自身生命被锁定在 1 点;自身受到的所有回血转化为等量伤害吸收,
 *     吸收上限为自身生命上限。</li>
 * </ul>
 * 判定方式:只要身上(护甲栏)有带该词缀的物品即生效;脱下立即恢复。
 */
@Mod.EventBusSubscriber(modid = SimulatedSpawner.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BloodWarHandler {

    /** 词缀 id:注册名 = simulatedspawner:armor/blood_war。 */
    public static final ResourceLocation AFFIX_ID = new ResourceLocation(SimulatedSpawner.MODID, "armor/blood_war");

    /** 生命锁定值。 */
    private static final float LOCKED_HEALTH = 1.0F;

    /** 目标身上是否有「血战」词缀。 */
    public static boolean hasBloodWar(LivingEntity entity) {
        for (ItemStack stack : entity.getArmorSlots()) {
            if (hasAffix(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAffix(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        try {
            return AffixHelper.streamAffixes(stack).anyMatch(instance -> AFFIX_ID.equals(AffixToggle.affixId(instance)));
        }
        catch (Throwable t) {
            return false;
        }
    }

    /** 生命锁定在 1 点。 */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !hasBloodWar(entity)) {
            return;
        }
        if (entity.getHealth() > LOCKED_HEALTH) {
            entity.setHealth(LOCKED_HEALTH);
        }
    }

    /** 回血全部转化为等量伤害吸收,吸收不超过自身生命上限。 */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !hasBloodWar(entity)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        event.setCanceled(true);
        float max = entity.getMaxHealth();
        float current = entity.getAbsorptionAmount();
        float updated = Math.min(max, current + amount);
        if (updated > current) {
            entity.setAbsorptionAmount(updated);
        }
        if (entity instanceof Player player && SimDebug.enabled()) {
            SimulationLogic.diagnose("血战:回血转吸收@" + player.getName().getString(),
                "血战:{} 点回血已转化为伤害吸收(当前 {}/{})。", amount, updated, max);
        }
    }
}
