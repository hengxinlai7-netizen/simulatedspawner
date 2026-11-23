package com.hengx.simulatedspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.SimulationLogic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * 模拟击杀窗口期内,把"被直接生成到世界里的掉落实体"改为收集进产物:
 * 有些模组(例如钱币模组)是在代码里直接把物品实体丢到世界里,而不是加到掉落表/事件列表里,
 * 不拦的话它们就会在刷怪笼周围飞溅。
 * 只在本模组的捕获窗口内(且限定在刷怪笼附近)生效。
 * 该方法的生产环境名为 SRG 名 m_7967_,故 remap = false。
 */
@Mixin(ServerLevel.class)
public class ServerLevelMixin {

    @Inject(method = "m_7967_", at = @At("HEAD"), cancellable = true, remap = false)
    private void simulatedspawner$captureDroppedItems(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ItemEntity item && SimulationLogic.tryCaptureItemEntity(item)) {
            cir.setReturnValue(true);
        }
    }
}
