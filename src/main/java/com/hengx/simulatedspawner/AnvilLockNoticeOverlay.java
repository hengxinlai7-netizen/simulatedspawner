package com.hengx.simulatedspawner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 铁砧界面上的「锁定后无法解锁」提示。
 * <p>
 * 选这个方案的原因:
 * <ul>
 * <li>提示必须只出现在<b>铁砧界面</b>里 —— 直接在 {@link AnvilScreen} 的渲染帧上叠加一行字,
 *     不需要改任何菜单/容器代码,也不会影响其它界面;</li>
 * <li>提示必须只出现在<b>执行锁定那一刻</b> —— 判定条件是"铁砧输出槽里出现了刚锁定的物品,
 *     而且输入槽还不是锁定物品",用的就是 {@link EnchantLockHandler#lockNotice} 这个可单测的方法;
 *     出现时开始计时,{@value #SHOW_MS} 毫秒后自动消失,输出槽被拿走就立即停止,
 *     因此永远不会变成界面上一行常驻的字;</li>
 * <li>纯客户端:不新增网络包、不改服务端逻辑,单人/联机行为一致(客户端自身就在跑铁砧菜单的逻辑)。</li>
 * </ul>
 * 附魔台那侧的锁定流程不受影响。
 */
@Mod.EventBusSubscriber(modid = SimulatedSpawner.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AnvilLockNoticeOverlay {

    /** 提示显示时长(毫秒)。 */
    private static final long SHOW_MS = 5000L;

    /** 提示颜色(金色)。 */
    private static final int COLOR = 0xFFAA00;

    private static long showUntil;

    private static boolean armed;

    private AnvilLockNoticeOverlay() {}

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AnvilScreen screen)) {
            armed = false;
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null || menu.slots.size() < 3) {
            return;
        }
        ItemStack input = menu.getSlot(0).getItem();
        ItemStack output = menu.getSlot(2).getItem();
        Component notice = EnchantLockHandler.lockNotice(input, output);
        long now = System.currentTimeMillis();
        if (notice == null) {
            armed = false;
            return;
        }
        if (!armed) {
            // 锁定刚刚生效:开始计时
            armed = true;
            showUntil = now + SHOW_MS;
        }
        if (now >= showUntil) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int width = font.width(notice);
        int x = (screen.width - width) / 2;
        int y = screen.height - 45;
        event.getGuiGraphics().drawString(font, notice, x, y, COLOR, true);
    }
}
