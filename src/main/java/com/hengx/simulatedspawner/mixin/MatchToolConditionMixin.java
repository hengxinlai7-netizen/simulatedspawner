package com.hengx.simulatedspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.BeaconRollFlag;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;

/**
 * 信标模式下,match_tool(要求击杀工具符合指定物品/附魔)条件直接通过,
 * 使模组用工具条件实现的特殊击杀掉落也能产出。
 * 该方法名在原版/生产环境均为 test,故 remap = false。
 */
@Mixin(MatchTool.class)
public class MatchToolConditionMixin {

    @Inject(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void simulatedspawner$beaconPass(LootContext context, CallbackInfoReturnable<Boolean> cir) {
        if (BeaconRollFlag.isActive()) {
            cir.setReturnValue(true);
        }
    }
}
