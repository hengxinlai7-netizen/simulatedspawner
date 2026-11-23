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
 * 自定义的刷怪笼 stat:"simulatedspawner:bonus_level"。
 * <p>
 * 数据包配方:主手钻石块 -> 等级 +1;主手钻石块 + 副手石英 -> 等级 -1。
 * 上限由通用配置 diamondBlockMaxUses 控制(默认 10):
 * <ul>
 * <li>模拟击杀:抢夺(Looting)等级 = 增益等级;</li>
 * <li>模拟钓鱼:海之眷顾(Luck of the Sea)等级 = 增益等级。</li>
 * </ul>
 */
public class BonusLevelStat implements SpawnerStat<Short> {

    public static final String ID = SimulatedSpawner.MODID + ":bonus_level";

    public static final BonusLevelStat INSTANCE = new BonusLevelStat();

    /** 与神化 ShortStat 相同的 codec:JSON/NBT 中为 {"id": "...", "value": 1, "min": 0, "max": 10}。 */
    private final Codec<StatModifier<Short>> codec = RecordCodecBuilder.create(inst -> inst
        .group(
            Codec.SHORT.fieldOf("value").forGetter(StatModifier::value),
            Codec.intRange(-1, Short.MAX_VALUE).xmap(Integer::shortValue, Short::intValue).fieldOf("min").forGetter(StatModifier::min),
            Codec.intRange(-1, Short.MAX_VALUE).xmap(Integer::shortValue, Short::intValue).fieldOf("max").forGetter(StatModifier::max))
        .apply(inst, (value, min, max) -> new StatModifier<>(this, value, min == -1 ? (short) 0 : min, max == -1 ? Short.MAX_VALUE : max)));

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public Codec<StatModifier<Short>> getModifierCodec() {
        return this.codec;
    }

    @Override
    public Short getValue(ApothSpawnerTile spawner) {
        if (spawner instanceof SimulatedAccess access) {
            return (short) access.simulatedspawner$getBonusLevel();
        }
        return 0;
    }

    @Override
    public Component getTooltip(ApothSpawnerTile spawner) {
        short level = this.getValue(spawner);
        return level > 0 ? this.name().append(": ").append(String.valueOf(level)).withStyle(ChatFormatting.AQUA) : CommonComponents.EMPTY;
    }

    @Override
    public boolean apply(Short value, Short min, Short max, ApothSpawnerTile spawner) {
        if (!(spawner instanceof SimulatedAccess access)) {
            return false;
        }
        int oldValue = access.simulatedspawner$getBonusLevel();
        int cap = SimCommonConfig.DIAMOND_BLOCK_MAX_USES.get();
        int newValue = Math.min(cap, Math.max(min, oldValue + value));
        access.simulatedspawner$setBonusLevel(newValue);
        return oldValue != newValue;
    }
}
