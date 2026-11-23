package com.hengx.simulatedspawner.mixin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.AffixToggle;

import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 词缀失效:被禁用的词条在神化读取词缀的<b>总入口</b>处被过滤掉,
 * 因此它对所有基于词缀的效果一律不可见——属性加成、掉落加成(modifyLoot)、
 * 各类事件钩子(破坏方块/耐久/附魔等级/箭矢等)都不会再触发。
 * <p>
 * 刷怪与击杀掉落走的是同一套入口(AffixInstance.modifyLoot),所以被禁用的词条不会再影响产物。
 */
@Mixin(AffixHelper.class)
public class AffixHelperMixin {

    @Inject(method = "getAffixes", at = @At("RETURN"), cancellable = true, remap = false)
    private static void simulatedspawner$filterDisabledAffixes(ItemStack stack,
        CallbackInfoReturnable<Map<DynamicHolder<? extends Affix>, AffixInstance>> cir) {
        Map<DynamicHolder<? extends Affix>, AffixInstance> original = cir.getReturnValue();
        if (original == null || original.isEmpty() || AffixToggle.getDisabled(stack).isEmpty()) {
            return;
        }
        cir.setReturnValue(simulatedspawner$filter(stack, original));
    }

    @Inject(method = "streamAffixes", at = @At("RETURN"), cancellable = true, remap = false)
    private static void simulatedspawner$filterDisabledAffixStream(ItemStack stack,
        CallbackInfoReturnable<Stream<AffixInstance>> cir) {
        Stream<AffixInstance> original = cir.getReturnValue();
        if (original == null || AffixToggle.getDisabled(stack).isEmpty()) {
            return;
        }
        cir.setReturnValue(original.filter(instance -> !simulatedspawner$isDisabled(stack, instance)));
    }

    private static Map<DynamicHolder<? extends Affix>, AffixInstance> simulatedspawner$filter(ItemStack stack,
        Map<DynamicHolder<? extends Affix>, AffixInstance> original) {
        Map<DynamicHolder<? extends Affix>, AffixInstance> filtered = new LinkedHashMap<>(original.size());
        for (Map.Entry<DynamicHolder<? extends Affix>, AffixInstance> entry : original.entrySet()) {
            if (!simulatedspawner$isDisabled(stack, entry.getValue())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private static boolean simulatedspawner$isDisabled(ItemStack stack, AffixInstance instance) {
        ResourceLocation id = AffixToggle.affixId(instance);
        return id != null && AffixToggle.isDisabled(stack, id);
    }
}
