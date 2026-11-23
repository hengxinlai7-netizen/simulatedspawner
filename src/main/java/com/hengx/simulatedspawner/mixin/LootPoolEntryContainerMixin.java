package com.hengx.simulatedspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.BeaconRollFlag;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;

/**
 * 信标模式下,绕过掉落表入口级的所有条件(实体/工具/伤害来源/概率/位置/天气/时间等,
 * 以及模组自定义的条件):判定方法 m_79639_ 直接返回 true。
 * 生产环境该方法为 SRG 名,故 remap = false。
 */
@Mixin(LootPoolEntryContainer.class)
public class LootPoolEntryContainerMixin {

    @Inject(method = "m_79639_", at = @At("HEAD"), cancellable = true, remap = false)
    private void simulatedspawner$bypassEntryConditions(LootContext context, CallbackInfoReturnable<Boolean> cir) {
        if (BeaconRollFlag.isActive()) {
            cir.setReturnValue(true);
        }
    }
}
