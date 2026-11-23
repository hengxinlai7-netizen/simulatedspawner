package com.hengx.simulatedspawner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.spawn.modifiers.SpawnerStat;
import dev.shadowsoffire.apotheosis.spawn.modifiers.StatModifier;
import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerTile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * 自定义的刷怪笼 stat:"simulatedspawner:beacon"。
 * <p>
 * 数据包配方:主手信标 -> 开启宝藏增益;主手信标 + 副手石英 -> 关闭。
 * <ul>
 * <li>模拟击杀:所有概率类特殊掉落(random_chance / random_chance_with_looting)必定掉落;</li>
 * <li>模拟钓鱼:额外产出原版宝藏池的全部物品各一份。</li>
 * </ul>
 */
public class BeaconStat implements SpawnerStat<Boolean> {

    public static final String ID = SimulatedSpawner.MODID + ":beacon";

    public static final BeaconStat INSTANCE = new BeaconStat();

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
            return access.simulatedspawner$isBeacon();
        }
        return false;
    }

    @Override
    public Component getTooltip(ApothSpawnerTile spawner) {
        return this.getValue(spawner) ? this.name().withStyle(ChatFormatting.GOLD) : CommonComponents.EMPTY;
    }

    @Override
    public boolean apply(Boolean value, Boolean min, Boolean max, ApothSpawnerTile spawner) {
        if (!(spawner instanceof SimulatedAccess access)) {
            return false;
        }
        boolean old = access.simulatedspawner$isBeacon();
        access.simulatedspawner$setBeacon(value);
        return old != value;
    }
}
