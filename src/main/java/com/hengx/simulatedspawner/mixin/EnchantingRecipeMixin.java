package com.hengx.simulatedspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.SimCommonConfig;

import dev.shadowsoffire.apotheosis.ench.table.EnchantingRecipe;
import dev.shadowsoffire.apotheosis.ench.table.EnchantingStatRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 神化"灌注附魔"(Infusion Enchanting):
 * 原版要求材料正确、青金石与经验足够,并且<b>位阶/量子化/阿卡那三项全部落在配方要求区间内</b>
 * (requirements 下限与 max_requirements 上限)。
 * <p>
 * 本改动让灌注<b>只需要满足最低要求</b>:只要材料正确且三项属性达到 requirements 下限即可灌注,
 * 不再受 max_requirements 上限限制(可由通用配置 infusionIgnoreMaxRequirements 关闭)。
 */
@Mixin(EnchantingRecipe.class)
public class EnchantingRecipeMixin {

    @Shadow(remap = false)
    protected Ingredient input;

    @Shadow(remap = false)
    protected EnchantingStatRegistry.Stats requirements;

    @Inject(method = "matches(Lnet/minecraft/world/item/ItemStack;FFF)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void simulatedspawner$onlyRequireMinimum(ItemStack inputStack, float eterna, float quanta, float arcana,
        CallbackInfoReturnable<Boolean> cir) {
        if (!SimCommonConfig.INFUSION_IGNORE_MAX_REQUIREMENTS.get()) {
            return;
        }
        // 材料不对时交回原版(结果为 false)
        if (!this.input.test(inputStack)) {
            return;
        }
        EnchantingStatRegistry.Stats min = this.requirements;
        if (min == null) {
            cir.setReturnValue(true);
            return;
        }
        // 只看最低要求(下限),忽略 max_requirements 上限
        cir.setReturnValue(eterna >= min.eterna() && quanta >= min.quanta() && arcana >= min.arcana());
    }
}
