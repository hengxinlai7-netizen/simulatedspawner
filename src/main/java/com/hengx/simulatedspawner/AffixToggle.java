package com.hengx.simulatedspawner;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.affix.augmenting.AugmentingMenu;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 强化台"词缀开关":
 * <ul>
 * <li>物品上已有的词条可以自由启用/禁用,禁用状态记录在物品自身的 NBT
 *     ({@code simulatedspawner:disabled_affixes})上,随物品一起保存与同步;</li>
 * <li>被禁用的词条不会出现在强化台的<b>刷新候选</b>里,界面上的勾选列表也可以把它们从可选词条中去掉,
 *     从而"不被强化台改动";</li>
 * <li>客户端通过原版菜单按钮包(button id = {@link #TOGGLE_BUTTON_BASE} + 词条序号)与服务端通信,
 *     不需要自建网络通道。</li>
 * </ul>
 */
public final class AffixToggle {

    /**
     * 勾选开关使用的菜单按钮 id 起始值(避开神化自己的 UPGRADE=0 / REROLL=1)。
     * 原版按钮包用 byte 传输按钮 id,因此必须小于 128。
     */
    public static final int TOGGLE_BUTTON_BASE = 64;

    private static final String TAG = "simulatedspawner:disabled_affixes";

    private AffixToggle() {}

    /** 读取物品上被禁用的词缀 id 集合。 */
    public static Set<ResourceLocation> getDisabled(ItemStack stack) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        if (stack.isEmpty() || !stack.hasTag()) {
            return result;
        }
        ListTag list = stack.getTag().getList(TAG, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    public static boolean isDisabled(ItemStack stack, ResourceLocation affix) {
        return affix != null && getDisabled(stack).contains(affix);
    }

    /** 设置某个词缀的启用/禁用状态。 */
    public static void setDisabled(ItemStack stack, ResourceLocation affix, boolean disabled) {
        if (stack.isEmpty() || affix == null) {
            return;
        }
        Set<ResourceLocation> current = getDisabled(stack);
        if (disabled) {
            if (!current.add(affix)) {
                return;
            }
        }
        else if (!current.remove(affix)) {
            return;
        }
        if (current.isEmpty()) {
            stack.getOrCreateTag().remove(TAG);
            return;
        }
        ListTag list = new ListTag();
        for (ResourceLocation id : current) {
            list.add(StringTag.valueOf(id.toString()));
        }
        stack.getOrCreateTag().put(TAG, list);
    }

    /**
     * 物品当前的词缀列表(顺序稳定,与界面里的序号一一对应)。
     * 这里特意使用<b>未过滤</b>的词缀读取入口,这样被禁用的词条依然出现在开关列表里,可以随时再启用。
     */
    public static List<AffixInstance> itemAffixes(ItemStack stack) {
        try {
            var all = dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper.getAffixesImpl(stack);
            if (all != null && !all.isEmpty()) {
                return new java.util.ArrayList<>(all.values());
            }
        }
        catch (Throwable ignored) {
            // 退回神化自己的实现
        }
        return AugmentingMenu.computeItemAffixes(stack);
    }

    /** 取词缀实例的注册 id;取不到时返回 null。 */
    public static ResourceLocation affixId(AffixInstance instance) {
        return holderId(instance == null ? null : instance.affix());
    }

    /** 取词缀实例的显示名。 */
    public static Component affixName(AffixInstance instance) {
        return instance == null ? Component.empty() : instance.getName(true);
    }

    private static ResourceLocation holderId(DynamicHolder<? extends Affix> holder) {
        if (holder == null) {
            return null;
        }
        try {
            Affix affix = holder.get();
            return affix == null ? null : affix.getId();
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 刷选候选过滤:剔除被禁用的词缀。 */
    public static List<DynamicHolder<? extends Affix>> filterAlternatives(ItemStack stack,
        List<DynamicHolder<? extends Affix>> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            return alternatives;
        }
        Set<ResourceLocation> disabled = getDisabled(stack);
        if (disabled.isEmpty()) {
            return alternatives;
        }
        java.util.ArrayList<DynamicHolder<? extends Affix>> filtered = new java.util.ArrayList<>(alternatives.size());
        for (DynamicHolder<? extends Affix> holder : alternatives) {
            ResourceLocation id = holderId(holder);
            if (id == null || !disabled.contains(id)) {
                filtered.add(holder);
            }
        }
        return filtered;
    }
}
