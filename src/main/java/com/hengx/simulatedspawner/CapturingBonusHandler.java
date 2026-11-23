package com.hengx.simulatedspawner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 神化"捕捉"附魔的附加效果:
 * <ul>
 * <li>击杀目标的<b>最大生命值</b>不大于 (捕捉等级 × 配置百分比 × 击杀者自身最大生命值) 时,<b>必定</b>掉落一个该生物的刷怪蛋;</li>
 * <li>默认百分比为 30%:捕捉 I → 目标血量 ≤ 击杀者 30% 血量,捕捉 III → ≤ 90%,依此类推;</li>
 * <li>该掉落为固定 1 个蛋,<b>不受抢夺附魔影响</b>;</li>
 * <li>与神化捕捉附魔原本的效果完全独立:原概率掉落(等级/250)照常结算,条件满足时本效果额外再掉一个蛋;</li>
 * <li>与神化一致:无刷怪蛋的生物类型、以及神化配置中封禁的生物不会掉落。</li>
 * </ul>
 */
public class CapturingBonusHandler {

    private static final ResourceLocation CAPTURING_ID = new ResourceLocation("apotheosis", "capturing");

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        // 与神化捕捉原逻辑一致:击杀者取伤害来源实体,附魔看其主手武器
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof LivingEntity livingKiller)) {
            return;
        }

        Enchantment capturing = ForgeRegistries.ENCHANTMENTS.getValue(CAPTURING_ID);
        if (capturing == null) {
            return;
        }

        int level = livingKiller.getMainHandItem().getEnchantmentLevel(capturing);
        if (level <= 0) {
            return;
        }

        LivingEntity killed = event.getEntity();

        // 与神化一致:封禁生物不掉蛋
        if (dev.shadowsoffire.apotheosis.spawn.SpawnerModule.bannedMobs.contains(EntityType.getKey(killed.getType()))) {
            return;
        }

        // 血量上限判定:目标最大生命值 <= 附魔等级 × 百分比 × 击杀者最大生命值
        double threshold = level * SimConfig.CAPTURING_BONUS_PERCENT.get() * livingKiller.getMaxHealth();
        if (killed.getMaxHealth() > threshold) {
            return;
        }

        Item eggItem = ForgeSpawnEggItem.fromEntityType(killed.getType());
        if (eggItem == null) {
            return;
        }

        ItemStack egg = new ItemStack(eggItem);
        event.getDrops().add(new ItemEntity(killed.level(), killed.getX(), killed.getY(), killed.getZ(), egg));
    }
}
