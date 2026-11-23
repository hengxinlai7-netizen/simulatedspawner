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
 * 自定义的刷怪笼 stat:"simulatedspawner:revelation"。
 * <p>
 * 数据包配方:主手神秘遗物的"启示之证"({@code enigmaticlegacy:the_acknowledgment})→ 开启;
 * 主手启示之证 + 副手石英 → 关闭。
 * <p>
 * 效果:模拟击杀时临时给假玩家戴上七咒之戒({@code enigmaticlegacy:cursed_ring}),
 * 使神秘遗物的"七咒之戒独特掉落"判定通过(翎羽/金锭/图腾等按生物类型的额外掉落)。
 */
public class RevelationStat implements SpawnerStat<Boolean> {

    public static final String ID = SimulatedSpawner.MODID + ":revelation";

    public static final RevelationStat INSTANCE = new RevelationStat();

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
            return access.simulatedspawner$isRevelation();
        }
        return false;
    }

    @Override
    public Component getTooltip(ApothSpawnerTile spawner) {
        return this.getValue(spawner) ? this.name().withStyle(ChatFormatting.LIGHT_PURPLE) : CommonComponents.EMPTY;
    }

    @Override
    public boolean apply(Boolean value, Boolean min, Boolean max, ApothSpawnerTile spawner) {
        if (!(spawner instanceof SimulatedAccess access)) {
            return false;
        }
        boolean old = access.simulatedspawner$isRevelation();
        access.simulatedspawner$setRevelation(value);
        return old != value;
    }
}
