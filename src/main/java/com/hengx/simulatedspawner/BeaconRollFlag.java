package com.hengx.simulatedspawner;

/**
 * 信标模式下的掉落掷取标记(线程级):
 * <ul>
 * <li>置为 true 期间,掉落表入口/池条件与最外层 GLM 中"击杀者一侧"的条件
 *     (概率、工具、击杀者实体属性)直接通过;</li>
 * <li>嵌套的掉落掷取(GLM 内部再掷其它表)保持正常条件判定,避免第三方 GLM
 *     在不完整的上下文里崩溃;</li>
 * <li>与掉落表 ID 相关的条件(forge:loot_table_id 等)不会被绕过,
 *     因此宝箱/结构战利品注入类的 GLM 不会误加到模拟击杀里;</li>
 * <li>深度由 {@code ForgeHooksMixin} 在 modifyLoot 入口/出口维护:
 *     我们自己的最外层调用深度为 1(生效),更深层的嵌套掷取不生效。</li>
 * </ul>
 */
public final class BeaconRollFlag {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final ThreadLocal<Integer> GLM_DEPTH = ThreadLocal.withInitial(() -> 0);

    private BeaconRollFlag() {}

    /** 处于信标掷取窗口且不在嵌套 GLM 掷取中时为 true(最外层 modifyLoot 深度为 1)。 */
    public static boolean isActive() {
        return ACTIVE.get() && GLM_DEPTH.get() <= 1;
    }

    public static void setActive(boolean active) {
        ACTIVE.set(active);
    }

    public static void enterGlm() {
        GLM_DEPTH.set(GLM_DEPTH.get() + 1);
    }

    public static void exitGlm() {
        int depth = GLM_DEPTH.get();
        if (depth > 0) {
            GLM_DEPTH.set(depth - 1);
        }
    }

    /** 防御性重置:异常时强制归零,避免深度计数残留导致条件永久绕过。 */
    public static void resetDepth() {
        GLM_DEPTH.set(0);
    }
}
