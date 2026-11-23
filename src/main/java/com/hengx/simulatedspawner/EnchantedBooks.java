package com.hengx.simulatedspawner;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * 附魔书读写工具:统一把附魔写进 <b>StoredEnchantments</b>。
 * <p>
 * 为什么必须这样写:1.20.1 的 {@code ItemStack#enchant(Enchantment,int)} 是把附魔写进
 * <b>Enchantments</b> 列表的,而附魔书的附魔存在 <b>StoredEnchantments</b> 里
 * ({@code EnchantedBookItem#getEnchantments}、{@code EnchantmentHelper#getEnchantments}、
 * {@code AnvilMenu#createResult} 读的都是后者)。
 * 用 {@code enchant()} 造出来的"附魔书"看起来是附魔书,实际在原版读取路径上没有任何附魔:
 * 提示栏不显示附魔、铁砧里也无法把附魔打上去 —— 也就是"做出来的书不能用"。
 * <p>
 * {@code read} 同时兼容旧版本(1.7.7 及更早)用 {@code enchant()} 写进 Enchantments 的坏书,
 * 这样老存档里的书仍然可以拿去升级配方。
 */
public final class EnchantedBooks {

    private EnchantedBooks() {}

    /** 按给定附魔表造一本附魔书(写入 StoredEnchantments,与原版附魔台/战利品得到的一致)。 */
    public static ItemStack create(Map<Enchantment, Integer> enchantments) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey();
            Integer level = entry.getValue();
            if (enchantment != null && level != null && level > 0) {
                EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(enchantment, level));
            }
        }
        return book;
    }

    /** 读取附魔书上的全部附魔(StoredEnchantments 优先,兼容旧版写错的 Enchantments)。 */
    public static Map<Enchantment, Integer> read(ItemStack stack) {
        Map<Enchantment, Integer> result = new LinkedHashMap<>(EnchantmentHelper.getEnchantments(stack));
        if (stack.is(Items.ENCHANTED_BOOK) && stack.hasTag()) {
            Map<Enchantment, Integer> legacy =
                EnchantmentHelper.deserializeEnchantments(stack.getEnchantmentTags());
            for (Map.Entry<Enchantment, Integer> entry : legacy.entrySet()) {
                result.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** 单个附魔的等级(0 = 没有)。 */
    public static int level(ItemStack stack, Enchantment enchantment) {
        return read(stack).getOrDefault(enchantment, 0);
    }
}
