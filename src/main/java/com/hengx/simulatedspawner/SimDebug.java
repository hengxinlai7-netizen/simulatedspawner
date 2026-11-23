package com.hengx.simulatedspawner;

import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 诊断日志的总闸门。
 * <p>
 * 1.7.9 起 {@code general.debugLog} 默认 <b>false</b>:模拟掉落/模拟钓鱼的"为什么没有产出"诊断、
 * 血战/锁定附魔/清道夫/黑名单等提示、以及强化台"血战为什么不在候选池"的逐条输出,
 * <b>在不打开开关时一行都不写</b>,并且调用点连日志字符串与玩家名都不拼(先查这里再拼)。
 * <p>
 * 这里保存的是一个 volatile 快照,读它不碰 {@link SimCommonConfig#SPEC}(避免事件热路径上走
 * ForgeConfigSpec 的取值/分配路径)。配置加载与热重载时刷新,读取失败一律按"关闭"处理。
 * <p>
 * 排查问题时把 <code>config/simulatedspawner-common.toml</code> 的 <code>general.debugLog</code>
 * 改成 true 即可(热重载即时生效),查完记得改回 false。
 */
public final class SimDebug {

    private static volatile boolean enabled = false;

    private SimDebug() {}

    /** 是否记录诊断日志。热路径上只读这一个 volatile 字段。 */
    public static boolean enabled() {
        return enabled;
    }

    /** 配置加载/热重载后刷新快照;任何异常都退化为"关闭"。 */
    static void refresh() {
        try {
            enabled = SimCommonConfig.DEBUG_LOG.get();
        }
        catch (Throwable t) {
            enabled = false;
        }
    }

    /** 配置加载(ModConfigEvent.Loading)。 */
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SimCommonConfig.SPEC) {
            refresh();
        }
    }

    /** 配置热重载(ModConfigEvent.Reloading)。 */
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SimCommonConfig.SPEC) {
            refresh();
        }
    }
}
