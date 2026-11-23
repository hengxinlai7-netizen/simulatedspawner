package com.hengx.simulatedspawner.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hengx.simulatedspawner.BeaconRollFlag;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;

/**
 * 信标模式下,绕过掉落表池级条件(m_79053_ 开头的组合条件谓词)。
 * 生产环境该方法为 SRG 名,故 remap = false。
 */
@Mixin(LootPool.class)
public class LootPoolMixin {

    @Redirect(method = "m_79053_", at = @At(value = "INVOKE",
        target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z", remap = false),
        remap = false)
    private boolean simulatedspawner$bypassPoolConditions(Predicate<LootContext> predicate, Object context) {
        return BeaconRollFlag.isActive() || predicate.test((LootContext) context);
    }
}
