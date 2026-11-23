package com.hengx.simulatedspawner;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * "无法破坏"附魔:
 * <ul>
 * <li>任何有耐久值的工具/武器/装备(EnchantmentCategory.BREAKABLE)都可以通过附魔书+铁砧附上;</li>
 * <li>效果由 {@code UnbreakableTagHandler} 实现:附上后直接给物品添加 {@code Unbreakable:1b} 标签
 *     (原版标签自带"不掉耐久"判定),并自动清除物品上的该附魔本身;</li>
 * <li>附魔台无法直接获得、村民不交易(isDiscoverable/isTradeable=false),只能由 1 本书 + 8 个黑曜石无序合成附魔书;</li>
 * <li>不限制砂轮:砂轮可正常修理带标签的物品(标签不会被砂轮移除)。</li>
 * </ul>
 */
public class UnbreakableEnchantment extends Enchantment {

    public UnbreakableEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.BREAKABLE, EquipmentSlot.values());
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 30;
    }

    @Override
    public int getMaxCost(int level) {
        return 60;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }
}
