package com.hengx.simulatedspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.BeaconRollFlag;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithLootingCondition;

/**
 * 信标模式下,random_chance_with_looting 条件直接通过(模拟击杀掉落全部概率类特殊掉落)。
 * 该方法名在原版/生产环境均为 test,故 remap = false。
 */
@Mixin(LootItemRandomChanceWithLootingCondition.class)
public class LootRandomChanceWithLootingConditionMixin {

    @Inject(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void simulatedspawner$beaconPass(LootContext context, CallbackInfoReturnable<Boolean> cir) {
        if (BeaconRollFlag.isActive()) {
            cir.setReturnValue(true);
        }
    }
}
