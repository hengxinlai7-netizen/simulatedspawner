package com.hengx.simulatedspawner;

import java.util.List;
import java.util.Map;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * "无法破坏"附魔的实际效果:
 * <ul>
 * <li>附魔后的效果 = 直接给物品添加 {@code Unbreakable:1b} 标签(原版标签自带"不掉耐久"判定);</li>
 * <li>物品带上该标签时,自动清除物品上的"无法破坏"附魔本身——附魔只作为一次性"转化器";</li>
 * <li>附魔书(不可损耗耐久的物品)不会被转化,可以继续用来给其它工具附魔;</li>
 * <li>不再限制砂轮:砂轮可以正常修理带标签的物品(标签不会被砂轮移除)。</li>
 * </ul>
 */
public class UnbreakableTagHandler {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }

        Player player = event.player;
        boolean changed = false;
        changed |= convert(player.getInventory().items);
        changed |= convert(player.getInventory().armor);
        changed |= convert(player.getInventory().offhand);
        if (changed) {
            player.getInventory().setChanged();
        }
    }

    private static boolean convert(List<ItemStack> stacks) {
        boolean changed = false;
        for (ItemStack stack : stacks) {
            if (convert(stack)) {
                changed = true;
            }
        }
        return changed;
    }

    private static boolean convert(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Enchantment enchantment = SimulatedSpawner.UNBREAKABLE.get();
        if (stack.getEnchantmentLevel(enchantment) <= 0) {
            return false;
        }

        // 附魔书等没有耐久概念的物品保留附魔,以便继续给工具打上
        if (!stack.getItem().isDamageable(stack)) {
            return false;
        }

        // 直接添加无法破坏标签,并清除"无法破坏"附魔本身
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        if (enchantments.remove(enchantment) != null) {
            EnchantmentHelper.setEnchantments(enchantments, stack);
        }
        return true;
    }
}
