package com.hengx.simulatedspawner;

import java.util.List;

import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.affix.augmenting.AugmentingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 强化台界面上的"词条开关"列表(仅客户端):
 * 用 Forge 的 GUI 事件绘制与处理点击,<b>不注入任何原版/模组方法</b>,
 * 因此不会出现 Mixin 注入失败导致客户端崩溃的问题。
 * <p>
 * 点击某一行时通过原版菜单按钮包(button id = {@link AffixToggle#TOGGLE_BUTTON_BASE} + 词条序号)
 * 通知服务端切换该词条的启用/禁用(禁用 = 该词条失效)。
 */
@Mod.EventBusSubscriber(modid = SimulatedSpawner.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AugmentingOverlay {

    private static final int LINE_HEIGHT = 11;
    private static final int LIST_WIDTH = 104;
    private static final int HEADER_GAP = 13;

    private AugmentingOverlay() {}

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AugmentingScreen screen)) {
            return;
        }
        List<AffixInstance> affixes = affixes(screen);
        if (affixes.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) {
            return;
        }
        int x = listX(screen);
        int y = listY(screen);
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        event.getGuiGraphics().drawString(mc.font, Component.translatable("gui.simulatedspawner.affix_toggle"), x, y, 0xFFD700, false);
        y += HEADER_GAP;

        ItemStack item = mainItem(screen);
        for (AffixInstance instance : affixes) {
            ResourceLocation id = AffixToggle.affixId(instance);
            boolean disabled = id != null && AffixToggle.isDisabled(item, id);
            boolean hovered = isInside(mouseX, mouseY, x, y);

            String mark = disabled ? "[ ] " : "[x] ";
            String name = AffixToggle.affixName(instance).getString();
            if (name.length() > 12) {
                name = name.substring(0, 12) + "…";
            }
            int color = disabled ? 0xFF6666 : 0x66FF66;
            event.getGuiGraphics().drawString(mc.font, mark + name, x, y, hovered ? 0xFFFF00 : color, false);
            y += LINE_HEIGHT;
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !(event.getScreen() instanceof AugmentingScreen screen)) {
            return;
        }
        List<AffixInstance> affixes = affixes(screen);
        if (affixes.isEmpty()) {
            return;
        }
        int x = listX(screen);
        int y = listY(screen) + HEADER_GAP;
        for (int i = 0; i < affixes.size(); i++, y += LINE_HEIGHT) {
            if (isInside(event.getMouseX(), event.getMouseY(), x, y)) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gameMode != null) {
                    mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, AffixToggle.TOGGLE_BUTTON_BASE + i);
                    event.setCanceled(true);
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onTooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        var disabled = AffixToggle.getDisabled(event.getItemStack());
        if (!disabled.isEmpty()) {
            event.getToolTip().add(Component.translatable("tooltip.simulatedspawner.affix_disabled", disabled.size())
                .withStyle(net.minecraft.ChatFormatting.DARK_RED));
        }
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= y && mouseY < y + LINE_HEIGHT;
    }

    private static int listX(AugmentingScreen screen) {
        return screen.getGuiLeft() - LIST_WIDTH - 4;
    }

    private static int listY(AugmentingScreen screen) {
        return screen.getGuiTop() + 6;
    }

    private static ItemStack mainItem(AugmentingScreen screen) {
        return screen.getMenu() == null ? ItemStack.EMPTY : screen.getMenu().getMainItem();
    }

    private static List<AffixInstance> affixes(AugmentingScreen screen) {
        ItemStack item = mainItem(screen);
        if (item.isEmpty()) {
            return List.of();
        }
        try {
            return AffixToggle.itemAffixes(item);
        }
        catch (Throwable t) {
            return List.of();
        }
    }
}
