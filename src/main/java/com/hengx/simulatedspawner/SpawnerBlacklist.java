package com.hengx.simulatedspawner;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 刷怪笼黑名单:通用配置 {@code config/simulatedspawner-common.toml} 里的 {@code spawnerBlacklist}。
 * <p>
 * 命中黑名单的刷怪蛋被放进(神化)刷怪笼时,交互会被直接取消:
 * 刷怪蛋不消耗(留在玩家手里 = 原样返还),笼子完全不被改动(不换生物、不消耗能量、不触发任何副作用)。
 * <p>
 * 匹配规则:默认按<b>实体注册名</b>精确匹配({@code minecraft:ravager});
 * 也接受直接写<b>刷怪蛋物品的注册名</b>({@code minecraft:ravager_spawn_egg}),
 * 以便覆盖那些无法从物品反查到实体的自定义刷怪蛋。
 */
public final class SpawnerBlacklist {

    private SpawnerBlacklist() {}

    /** 读配置:去空白、转小写、去掉重复项。 */
    public static Set<String> entries() {
        return normalize(SimCommonConfig.SPAWNER_BLACKLIST.get());
    }

    /** 把配置里的字符串规整成可比较的集合。 */
    public static Set<String> normalize(@Nullable List<? extends String> raw) {
        Set<String> set = new LinkedHashSet<>();
        if (raw == null) {
            return set;
        }
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String value = entry.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                set.add(value);
            }
        }
        return set;
    }

    /** 刷怪蛋对应的实体类型;不是刷怪蛋或取不到时返回 null。 */
    @Nullable
    public static EntityType<?> eggType(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof SpawnEggItem egg)) {
            return null;
        }
        try {
            return egg.getType(stack.getTag());
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 按实体注册名判断某个实体是否在黑名单里。 */
    public static boolean isBlacklisted(EntityType<?> type, Set<String> blacklist) {
        if (type == null || blacklist.isEmpty()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return key != null && blacklist.contains(key.toString().toLowerCase(Locale.ROOT));
    }

    /** 按物品注册名判断某个物品是否在黑名单里(兜底:自定义刷怪蛋查不到实体时用)。 */
    public static boolean isBlacklisted(Item item, Set<String> blacklist) {
        if (item == null || blacklist.isEmpty()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key != null && blacklist.contains(key.toString().toLowerCase(Locale.ROOT));
    }

    /** 这个物品是否是"黑名单生物的刷怪蛋"。 */
    public static boolean isBlacklisted(ItemStack stack) {
        Set<String> blacklist = entries();
        if (stack.isEmpty() || blacklist.isEmpty()) {
            return false;
        }
        return isBlacklisted(eggType(stack), blacklist) || isBlacklisted(stack.getItem(), blacklist);
    }
}
