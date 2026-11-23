package com.hengx.simulatedspawner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.shadowsoffire.apotheosis.spawn.modifiers.SpawnerStat;
import dev.shadowsoffire.apotheosis.spawn.modifiers.SpawnerStats;
import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerTile;

/**
 * 创造模式刷怪笼配置界面的数据层:stat 列表、每项的步长与上下限、以及读写。
 * <p>
 * 写入统一走神化自己的 {@link SpawnerStat#apply(Object, Object, Object, ApothSpawnerTile)},
 * 也就是"应用一次升级材料"的同一条代码路径 —— 因此界面上点一下 = 用对应材料升级一次:
 * <ul>
 * <li>数值项:左侧减、右侧加,点一次走该 stat 在神化配方里的步长(最小/最大延迟 10,最大实体/激活范围 2,其余 1);</li>
 * <li>布尔项:直接切换;</li>
 * <li>本模组的"模拟掉落 / 模拟钓鱼"互斥逻辑照常生效;创造模式下不返还材料
 *     ({@link UpgradeContext} 在菜单里未设置,返还自动跳过)。</li>
 * </ul>
 */
public final class SpawnerConfigStats {

    /** 每个 stat 占用的按钮 id 数量(id = index * ACTIONS + action, 必须小于 128)。 */
    public static final int ACTIONS = 4;

    /** 最多显示的行数。 */
    public static final int MAX_ROWS = 24;

    /** 一行可配置项。 */
    public static final class Row {

        public final SpawnerStat<?> stat;

        public final String id;

        public final int step;

        public final int min;

        public final int max;

        Row(SpawnerStat<?> stat, int step, int min, int max) {
            this.stat = stat;
            this.id = stat.getId();
            this.step = step;
            this.min = min;
            this.max = max;
        }
    }

    private SpawnerConfigStats() {}

    /** 排序后的 stat 列表(两端一致:按 id 排序,神化自带的在前,本模组的在后)。 */
    public static List<Row> rows() {
        List<SpawnerStat<?>> stats = new ArrayList<>(SpawnerStats.REGISTRY.values());
        stats.sort(Comparator.comparing(SpawnerStat::getId));
        List<Row> list = new ArrayList<>();
        for (SpawnerStat<?> stat : stats) {
            if (list.size() >= MAX_ROWS) {
                break;
            }
            String id = stat.getId();
            list.add(new Row(stat, step(id), min(id), max(id)));
        }
        return list;
    }

    /** 去掉命名空间:神化自带 stat 的 id 是裸名(min_delay 等),本模组的是 simulatedspawner:xxx。 */
    private static String bareId(String id) {
        int index = id.indexOf(':');
        return index >= 0 ? id.substring(index + 1) : id;
    }

    /** 步长:与神化升级材料的增量保持一致。 */
    private static int step(String id) {
        return switch (bareId(id)) {
            case "min_delay", "max_delay" -> 10;
            case "max_nearby_entities", "req_player_range" -> 2;
            default -> 1;
        };
    }

    private static int min(String id) {
        return switch (bareId(id)) {
            case "min_delay", "max_delay" -> 1;
            default -> 0;
        };
    }

    private static int max(String id) {
        return switch (bareId(id)) {
            // 延迟是 Short stat,上限不能超过 short 范围
            case "min_delay", "max_delay" -> Short.MAX_VALUE;
            // 每次数量:是否解除 16 上限跟随本模组的通用配置
            case "spawn_count" -> SimCommonConfig.REMOVE_SPAWN_COUNT_LIMIT.get() ? 128 : 16;
            case "max_nearby_entities" -> 256;
            case "req_player_range" -> 128;
            case "spawn_range" -> 64;
            case "bonus_level" -> SimCommonConfig.DIAMOND_BLOCK_MAX_USES.get();
            default -> 255;
        };
    }

    /** 该 stat 是否是布尔项(读不到方块实体时按数值项处理)。 */
    public static boolean isBool(ApothSpawnerTile tile, Row row) {
        if (tile == null) {
            return false;
        }
        try {
            return row.stat.getValue(tile) instanceof Boolean;
        }
        catch (Throwable t) {
            return false;
        }
    }

    /** 读取当前值(布尔 -> 0/1)。 */
    public static int read(ApothSpawnerTile tile, Row row) {
        if (tile == null) {
            return 0;
        }
        try {
            Object value = row.stat.getValue(tile);
            if (value instanceof Boolean bool) {
                return bool ? 1 : 0;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
        }
        catch (Throwable t) {
            SimulatedSpawner.LOGGER.debug("读取刷怪笼 stat {} 失败", row.id, t);
        }
        return 0;
    }

    /**
     * 应用一个动作。
     *
     * @param action 0 = 减一步,1 = 加一步,2 = 大幅减(布尔项=切换),3 = 大幅加
     * @return 是否真的发生了变化
     */
    @SuppressWarnings("unchecked")
    public static boolean write(ApothSpawnerTile tile, Row row, int action) {
        if (tile == null) {
            return false;
        }
        // 配置界面不是"用材料升级",不需要返还材料:清掉上下文,让 stat 内部的返还逻辑自动跳过
        UpgradeContext.clear();
        try {
            Object value = row.stat.getValue(tile);
            if (value instanceof Boolean bool) {
                boolean target = switch (action) {
                    case 0 -> false;
                    case 1 -> true;
                    default -> !bool;
                };
                return ((SpawnerStat<Boolean>) row.stat).apply(target, null, null, tile);
            }
            if (value instanceof Short) {
                int delta = switch (action) {
                    case 0 -> -row.step;
                    case 1 -> row.step;
                    case 2 -> -row.step * 10;
                    default -> row.step * 10;
                };
                return ((SpawnerStat<Short>) row.stat).apply((short) delta, toShort(row.min), toShort(row.max), tile);
            }
        }
        catch (Throwable t) {
            SimulatedSpawner.LOGGER.warn("配置刷怪笼 stat {} 失败", row.id, t);
        }
        return false;
    }

    private static short toShort(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}
