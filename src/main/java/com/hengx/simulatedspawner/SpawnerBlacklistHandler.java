package com.hengx.simulatedspawner;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 刷怪笼黑名单:黑名单生物的刷怪蛋放进刷怪笼时,<b>退蛋 + 取消修改</b>。
 * <p>
 * 覆盖范围(逐一确认过的"把刷怪蛋放进刷怪笼"入口):
 * <ol>
 * <li><b>右键</b>:原版 {@code SpawnEggItem#useOn} 是唯一真正会"设置笼子生物"的入口
 *     (神化自己的 {@code ApothSpawnerBlock#use} 只处理 SpawnerModifier 配方,而
 *     {@code SpawnerModifier} 的物品里没有任何刷怪蛋,已用字节码确认);
 *     本处理器在 {@code PlayerInteractEvent.RightClickBlock} 阶段判定 —— 这是
 *     {@code ServerPlayerGameMode#useItemOn} / 客户端 {@code MultiPlayerGameMode} 在调用
 *     方块 {@code use} 与物品 {@code useOn} 之前的分叉点,取消后两者都不会执行,
 *     因此刷怪蛋不会被消耗(原样留在玩家手里 = 返还),笼子也完全不被改动
 *     (不换生物、不改已有生物、不消耗任何材料/能量)。神化自己处理 banned mobs 用的也是这个事件。</li>
 * <li><b>漏斗/自动化</b>:原版与神化都没有"把刷怪蛋插进刷怪笼"的物品栏接口
 *     ({@code ApothSpawnerTile} 不提供 {@code IForgeCapabilities.ITEM_HANDLER},
 *     发射器对刷怪蛋也没有特殊行为),因此不存在需要额外拦截的自动化入口。</li>
 * <li><b>神化自己的接口</b>:{@code SpawnerModifier} 升级材料(时钟/糖/发酵蛛眼等)与刷怪蛋无关;
 *     神化自带的 {@code spawn_eggs.bannedMobs} 配置同样走 RightClickBlock 取消,与本功能并存不冲突。</li>
 * </ol>
 * 判定条件用 {@code SpawnerBlockEntity}(原版/神化以及任何继承它的模组刷怪笼),
 * 正好等于"刷怪蛋能设置生物的方块"集合;非刷怪笼方块、非黑名单刷怪蛋完全不受影响。
 */
public class SpawnerBlacklistHandler {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (!(blockEntity instanceof SpawnerBlockEntity)) {
            return;
        }
        if (!handle(event.getEntity(), event.getItemStack())) {
            return;
        }
        // 退蛋 + 取消修改:在 SpawnEggItem#useOn 之前取消整个交互。
        event.setCanceled(true);
    }

    /**
     * 命中黑名单则提示玩家并返回 true,调用方据此取消交互。
     * 拆成独立方法便于自检直接断言。
     */
    public static boolean handle(Player player, ItemStack stack) {
        if (!SpawnerBlacklist.isBlacklisted(stack)) {
            return false;
        }
        if (player != null) {
            player.displayClientMessage(Component.translatableWithFallback(
                "message.simulatedspawner.spawn_egg_blacklisted",
                "该生物在刷怪笼黑名单中,刷怪蛋已退回"), true);
        }
        if (SimDebug.enabled()) {
            SimulationLogic.diagnose("黑名单刷怪蛋:" + stack.getItem(),
                "刷怪笼黑名单:{} 在黑名单里,刷怪蛋已退回,刷怪笼未做任何改动。", stack.getItem());
        }
        return true;
    }
}
