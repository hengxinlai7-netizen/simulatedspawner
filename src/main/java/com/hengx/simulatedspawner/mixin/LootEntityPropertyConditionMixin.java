package com.hengx.simulatedspawner.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.BeaconRollFlag;

import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;

/**
 * 信标模式下,绕过"击杀者一侧"的 entity_properties 条件
 * (例如农夫乐事清道夫掉落要求击杀者手持刀具 killer.mainhand=tag:knives)。
 * <p>
 * 只绕过与击杀者/伤害来源相关的条件:如果条件同时引用了 this_entity,
 * 说明它在限定"被杀的是哪种生物",这类条件照常判定,
 * 避免把某种生物专属的特殊掉落错误地塞给其它生物。
 * 该方法名在原版/生产环境均为 test,故 remap = false。
 */
@Mixin(LootItemEntityPropertyCondition.class)
public class LootEntityPropertyConditionMixin {

    @Inject(method = "test(Lnet/minecraft/world/level/storage/loot/LootContext;)Z",
        at = @At("HEAD"), cancellable = true, remap = false)
    private void simulatedspawner$bypassKillerConditions(LootContext context, CallbackInfoReturnable<Boolean> cir) {
        if (!BeaconRollFlag.isActive()) {
            return;
        }
        Set<LootContextParam<?>> params = ((LootItemEntityPropertyCondition) (Object) this).getReferencedContextParams();
        boolean killerSide = params.contains(LootContextParams.KILLER_ENTITY)
            || params.contains(LootContextParams.DIRECT_KILLER_ENTITY)
            || params.contains(LootContextParams.LAST_DAMAGE_PLAYER);
        if (killerSide && !params.contains(LootContextParams.THIS_ENTITY)) {
            cir.setReturnValue(true);
        }
    }
}
