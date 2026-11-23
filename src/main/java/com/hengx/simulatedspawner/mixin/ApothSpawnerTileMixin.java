package com.hengx.simulatedspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.simulatedspawner.SimulatedAccess;

import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerTile;
import net.minecraft.nbt.CompoundTag;

/**
 * 给神化刷怪笼方块实体添加本模组的状态字段,并随 NBT 持久化:
 * "模拟掉落"(钻石剑)、"模拟钓鱼"(钓鱼竿)、"增益等级"(钻石块)、"宝藏增益"(信标)。
 */
@Mixin(ApothSpawnerTile.class)
public class ApothSpawnerTileMixin implements SimulatedAccess {

    @Unique
    private boolean simulatedspawner$simulated;

    @Unique
    private boolean simulatedspawner$fishing;

    @Unique
    private int simulatedspawner$bonusLevel;

    @Unique
    private boolean simulatedspawner$beacon;

    @Unique
    private boolean simulatedspawner$revelation;

    @Override
    public boolean simulatedspawner$isSimulated() {
        return this.simulatedspawner$simulated;
    }

    @Override
    public void simulatedspawner$setSimulated(boolean simulated) {
        this.simulatedspawner$simulated = simulated;
    }

    @Override
    public boolean simulatedspawner$isFishing() {
        return this.simulatedspawner$fishing;
    }

    @Override
    public void simulatedspawner$setFishing(boolean fishing) {
        this.simulatedspawner$fishing = fishing;
    }

    @Override
    public int simulatedspawner$getBonusLevel() {
        return this.simulatedspawner$bonusLevel;
    }

    @Override
    public void simulatedspawner$setBonusLevel(int level) {
        this.simulatedspawner$bonusLevel = level;
    }

    @Override
    public boolean simulatedspawner$isBeacon() {
        return this.simulatedspawner$beacon;
    }

    @Override
    public void simulatedspawner$setBeacon(boolean beacon) {
        this.simulatedspawner$beacon = beacon;
    }

    @Override
    public boolean simulatedspawner$isRevelation() {
        return this.simulatedspawner$revelation;
    }

    @Override
    public void simulatedspawner$setRevelation(boolean revelation) {
        this.simulatedspawner$revelation = revelation;
    }

    @Inject(at = @At("TAIL"), method = "saveAdditional")
    private void simulatedspawner$save(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean("simulatedspawner:simulated", this.simulatedspawner$simulated);
        tag.putBoolean("simulatedspawner:fishing", this.simulatedspawner$fishing);
        tag.putInt("simulatedspawner:bonus_level", this.simulatedspawner$bonusLevel);
        tag.putBoolean("simulatedspawner:beacon", this.simulatedspawner$beacon);
        tag.putBoolean("simulatedspawner:revelation", this.simulatedspawner$revelation);
    }

    @Inject(at = @At("TAIL"), method = "load")
    private void simulatedspawner$load(CompoundTag tag, CallbackInfo ci) {
        this.simulatedspawner$simulated = tag.getBoolean("simulatedspawner:simulated");
        this.simulatedspawner$fishing = tag.getBoolean("simulatedspawner:fishing");
        this.simulatedspawner$bonusLevel = tag.getInt("simulatedspawner:bonus_level");
        this.simulatedspawner$beacon = tag.getBoolean("simulatedspawner:beacon");
        this.simulatedspawner$revelation = tag.getBoolean("simulatedspawner:revelation");
    }
}
