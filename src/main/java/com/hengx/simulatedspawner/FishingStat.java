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
 * 自定义的刷怪笼 stat:"simulatedspawner:fishing"。
 * <p>
 * 通过数据包配方(data/simulatedspawner/recipes/fishing*.json)应用:
 * 主手钓鱼竿 -> 开启模拟钓鱼;主手钓鱼竿 + 副手石英 -> 关闭模拟钓鱼。
 */
public class FishingStat implements SpawnerStat<Boolean> {

    public static final String ID = SimulatedSpawner.MODID + ":fishing";

    public static final FishingStat INSTANCE = new FishingStat();

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
            return access.simulatedspawner$isFishing();
        }
        return false;
    }

    @Override
    public Component getTooltip(ApothSpawnerTile spawner) {
        return this.getValue(spawner) ? this.name().withStyle(ChatFormatting.AQUA) : CommonComponents.EMPTY;
    }

    @Override
    public boolean apply(Boolean value, Boolean min, Boolean max, ApothSpawnerTile spawner) {
        if (!(spawner instanceof SimulatedAccess access)) {
            return false;
        }
        boolean old = access.simulatedspawner$isFishing();
        if (value && !old && access.simulatedspawner$isSimulated()) {
            // 两种模拟升级互斥:开启模拟钓鱼时,移除模拟掉落并返还钻石剑
            access.simulatedspawner$setSimulated(false);
            UpgradeContext.refund(Items.DIAMOND_SWORD, Component.translatable("stat.apotheosis." + SimulatedStat.ID));
        }
        access.simulatedspawner$setFishing(value);
        return old != value;
    }
}
