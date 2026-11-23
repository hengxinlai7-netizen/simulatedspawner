package com.hengx.simulatedspawner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 神化"清道夫"附魔的新增机制:
 * <ul>
 * <li>原本效果(按等级有概率再掉一次战利品)保持不动;</li>
 * <li>新增:若目标在 <b>满血状态下被一击击杀</b>,且击杀者主手武器带有清道夫附魔,
 *     则<b>必定</b>额外再掷一次该生物的掉落表(即必定双倍掉落);</li>
 * <li>该次结算与原版概率效果<b>相互独立</b>,两者可能同时触发。</li>
 * </ul>
 */
public class ScavengerBonusHandler {

    private static final ResourceLocation SCAVENGER_ID = new ResourceLocation("apotheosis", "scavenger");

    /** 记录"满血被一击击杀"的生物,等待掉落结算时使用。 */
    private static final Map<UUID, Long> ONE_SHOT_KILLS = new HashMap<>();

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob.level().isClientSide()) {
            return;
        }
        float health = mob.getHealth();
        if (health <= 0.0F || health < mob.getMaxHealth() - 1.0E-4F) {
            return; // 不是满血,不算一击击杀
        }
        if (event.getAmount() < health) {
            return; // 这一击不足以打死
        }
        ONE_SHOT_KILLS.put(mob.getUUID(), mob.level().getGameTime());
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!SimCommonConfig.SCAVENGER_ONE_SHOT_DOUBLES.get()) {
            return;
        }
        LivingEntity killed = event.getEntity();
        if (ONE_SHOT_KILLS.remove(killed.getUUID()) == null) {
            return;
        }

        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof LivingEntity livingKiller)) {
            return;
        }
        Enchantment scavenger = ForgeRegistries.ENCHANTMENTS.getValue(SCAVENGER_ID);
        if (scavenger == null || livingKiller.getMainHandItem().getEnchantmentLevel(scavenger) <= 0) {
            return;
        }

        List<ItemStack> extra = SimulationLogic.rollExtraKillDrops(killed, event.getSource(), livingKiller, event.getLootingLevel());
        for (ItemStack stack : extra) {
            if (!stack.isEmpty()) {
                event.getDrops().add(new ItemEntity(killed.level(), killed.getX(), killed.getY(), killed.getZ(), stack));
            }
        }
        if (!extra.isEmpty() && SimDebug.enabled()) {
            SimulationLogic.diagnose("清道夫一击双倍@" + killed.getType(),
                "清道夫:{} 被一击击杀,已额外结算一次掉落({} 件)。", killed.getType(), extra.size());
        }
    }
}
