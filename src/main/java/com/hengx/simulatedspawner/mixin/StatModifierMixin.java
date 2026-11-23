package com.hengx.simulatedspawner.mixin;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.SimCommonConfig;
import com.hengx.simulatedspawner.SimulatedSpawner;

import dev.shadowsoffire.apotheosis.spawn.modifiers.SpawnerStat;
import dev.shadowsoffire.apotheosis.spawn.modifiers.StatModifier;
import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerTile;
import net.minecraft.world.level.BaseSpawner;

/**
 * 移除神化"每次数量"(spawn_count)升级的 16 上限:
 * 配置 removeSpawnCountLimit=true 时,spawn_count 的修改不再受配方 max 限制,
 * 下限(min)仍按原配方生效,其余升级(速度/范围/红石控制等)走原逻辑不受影响。
 */
@Mixin(StatModifier.class)
public abstract class StatModifierMixin {

    /** 生产环境为 SRG 名 f_45449_,开发环境为 mojmap 名 spawnCount,两者都尝试。 */
    private static final Field SPAWN_COUNT_FIELD = findSpawnCountField();

    private static Field findSpawnCountField() {
        for (String name : new String[] { "spawnCount", "f_45449_" }) {
            try {
                Field field = BaseSpawner.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException e) {
                // try next name
            }
        }
        return null;
    }

    // apply 是神化自己的方法,开发/生产环境名称一致,无需重映射(remap = false)
    @Inject(method = "apply", at = @At("HEAD"), cancellable = true, remap = false)
    private void simulatedspawner$removeSpawnCountLimit(ApothSpawnerTile tile, CallbackInfoReturnable<Boolean> cir) {
        if (!SimCommonConfig.REMOVE_SPAWN_COUNT_LIMIT.get()) {
            return;
        }

        StatModifier<?> self = (StatModifier<?>) (Object) this;
        SpawnerStat<?> stat = self.stat();
        if (!"spawn_count".equals(stat.getId())) {
            return;
        }

        BaseSpawner logic = tile.getSpawner();
        if (logic == null || SPAWN_COUNT_FIELD == null) {
            return;
        }

        try {
            int oldValue = SPAWN_COUNT_FIELD.getInt(logic);
            int value = ((Number) self.value()).intValue();
            int min = ((Number) self.min()).intValue();
            int newValue = Math.max(min, oldValue + value);
            SPAWN_COUNT_FIELD.setInt(logic, newValue);
            cir.setReturnValue(oldValue != newValue);
        }
        catch (ReflectiveOperationException ex) {
            SimulatedSpawner.LOGGER.warn("无法解除 spawn_count 升级的上限", ex);
        }
    }
}
