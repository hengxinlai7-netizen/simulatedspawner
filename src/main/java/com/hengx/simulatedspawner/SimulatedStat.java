package com.hengx.simulatedspawner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.spawn.modifiers.SpawnerStat;
import dev.shadowsoffire.apotheosis.spawn.modifiers.StatModifier;
import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerTile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/**
 * 自定义的刷怪笼 stat:"simulatedspawner:simulated"。
 * <p>
 * 通过数据包配方(data/simulatedspawner/recipes/*.json)应用:
 * 主手钻石剑 -> 开启模拟掉落;主手钻石剑 + 副手石英 -> 关闭模拟掉落。
 */
public class SimulatedStat implements SpawnerStat<Boolean> {

    public static final String ID = SimulatedSpawner.MODID + ":simulated";

    public static final SimulatedStat INSTANCE = new SimulatedStat();

    /** 与神化 BoolStat 相同的 codec:JSON/NBT 中只需要 {"id": "...", "value": true}。 */
    private final Codec<StatModifier<Boolean>> codec = RecordCodecBuilder.create(inst -> inst
        .group(Codec.BOOL.fieldOf("value").forGetter(StatModifier::value))
        .apply(inst, value -> new StatModifier<>(this, value, false, true)));

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Codec<StatModifier<Boolean>> getModifierCodec() {
        return this.codec;
    }

    @Override
    public Boolean getValue(ApothSpawnerTile spawner) {
        if (spawner instanceof SimulatedAccess access) {
            return access.simulatedspawner$isSimulated();
        }
        return false;
    }

    @Override
    public Component getTooltip(ApothSpawnerTile spawner) {
        return this.getValue(spawner) ? this.name().withStyle(ChatFormatting.DARK_GREEN) : CommonComponents.EMPTY;
    }

    @Override
    public boolean apply(Boolean value, Boolean min, Boolean max, ApothSpawnerTile spawner) {
        if (!(spawner instanceof SimulatedAccess access)) {
            return false;
        }
        boolean old = access.simulatedspawner$isSimulated();
        if (value && !old && access.simulatedspawner$isFishing()) {
            // 两种模拟升级互斥:开启模拟掉落时,移除模拟钓鱼并返还钓鱼竿
            access.simulatedspawner$setFishing(false);
            UpgradeContext.refund(Items.FISHING_ROD, Component.translatable("stat.apotheosis." + FishingStat.ID));
        }
        access.simulatedspawner$setSimulated(value);
        return old != value;
    }
}
