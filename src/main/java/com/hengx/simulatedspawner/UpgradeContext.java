package com.hengx.simulatedspawner;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 升级应用上下文。
 * <p>
 * 神化在 {@code ApothSpawnerBlock#use} 中匹配并应用刷怪笼升级配方,那里能拿到玩家,
 * 由 {@code ApothSpawnerBlockMixin} 把玩家暂存于此,供我们的 stat 在需要"返还材料"时使用
 * (我们的 {@code SpawnerStat#apply} 只有方块实体参数,拿不到玩家)。
 */
public final class UpgradeContext {

    @Nullable
    private static ServerPlayer player;

    private UpgradeContext() {}

    public static void set(@Nullable ServerPlayer value) {
        player = value;
    }

    @Nullable
    public static ServerPlayer get() {
        return player;
    }

    public static void clear() {
        player = null;
    }

    /**
     * 把材料返还给当前交互的玩家(背包放不下就丢在脚下),并在动作栏提示一条消息。
     *
     * @param item                要返还的材料
     * @param removedUpgradeName  被移除的升级名称
     */
    public static void refund(Item item, Component removedUpgradeName) {
        ServerPlayer target = player;
        if (target == null) {
            return;
        }
        ItemStack refund = new ItemStack(item);
        if (!target.getInventory().add(refund)) {
            target.drop(refund, false);
        }
        try {
            target.displayClientMessage(Component.translatable("message.simulatedspawner.upgrade_replaced",
                removedUpgradeName, refund.getHoverName()), true);
        }
        catch (Throwable t) {
            // 某些假玩家没有网络连接,发消息可能失败;不影响返还本身
            SimulatedSpawner.LOGGER.debug("发送升级替换提示失败", t);
        }
    }
}
