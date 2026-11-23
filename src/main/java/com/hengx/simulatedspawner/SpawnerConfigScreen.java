package com.hengx.simulatedspawner;

import java.util.ArrayList;
import java.util.List;

import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerTile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 创造模式刷怪笼配置界面(仅客户端)。
 * <p>
 * 只绘制文字与色块,不使用贴图、没有物品槽位;数值来自 {@link SpawnerConfigMenu} 的同步数据,
 * 点击通过原版菜单按钮包发给服务端。
 */
@OnlyIn(Dist.CLIENT)
public class SpawnerConfigScreen extends AbstractContainerScreen<SpawnerConfigMenu> {

    private static final int ROW_H = 13;

    private static final int HEADER_H = 22;

    private static final int LABEL_X = 8;

    private static final int VALUE_RIGHT = 178;

    private static final int MINUS_X = 184;

    private static final int PLUS_X = 208;

    private static final int BTN_W = 20;

    private static final int BTN_H = 11;

    private static final int PANEL_W = 244;

    public SpawnerConfigScreen(SpawnerConfigMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = HEADER_H + menu.rowCount() * ROW_H + 10;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        // 没有玩家物品栏,把"物品栏"标题移出画面
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xC0101010);
        graphics.fill(x, y, x + this.imageWidth, y + HEADER_H - 6, 0xFF2B2B2B);

        List<SpawnerConfigStats.Row> rows = this.menu.rows();
        for (int i = 0; i < rows.size(); i++) {
            int rowY = y + HEADER_H + i * ROW_H;
            if (i % 2 == 0) {
                graphics.fill(x + 2, rowY, x + this.imageWidth - 2, rowY + ROW_H - 1, 0x28FFFFFF);
            }
            if (this.menu.isBool(i)) {
                this.drawButton(graphics, x + MINUS_X, rowY, x + this.imageWidth - 6, rowY + BTN_H,
                    Component.translatable("gui.simulatedspawner.toggle").getString(), mouseX, mouseY);
            }
            else {
                this.drawButton(graphics, x + MINUS_X, rowY, x + MINUS_X + BTN_W, rowY + BTN_H, "-", mouseX, mouseY);
                this.drawButton(graphics, x + PLUS_X, rowY, x + PLUS_X + BTN_W, rowY + BTN_H, "+", mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);
        String hint = Component.translatable("gui.simulatedspawner.spawner_config.hint").getString();
        graphics.drawString(this.font, hint, this.imageWidth - 8 - this.font.width(hint), this.titleLabelY, 0x909090, false);

        List<SpawnerConfigStats.Row> rows = this.menu.rows();
        for (int i = 0; i < rows.size(); i++) {
            SpawnerConfigStats.Row row = rows.get(i);
            int rowY = HEADER_H + i * ROW_H + 2;
            String label = row.stat.name().getString();
            if (label.length() > 16) {
                label = label.substring(0, 15) + "…";
            }
            graphics.drawString(this.font, label, LABEL_X, rowY, 0xE0E0E0, false);

            String value = this.menu.isBool(i)
                ? Component.translatable(this.menu.value(i) != 0 ? "gui.simulatedspawner.on" : "gui.simulatedspawner.off").getString()
                : String.valueOf(this.menu.value(i));
            graphics.drawString(this.font, value, VALUE_RIGHT - this.font.width(value), rowY, 0xFFFF80, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderRowTooltip(graphics, mouseX, mouseY);
    }

    /** 悬停在名称/数值区域时显示该 stat 的说明与当前值描述。 */
    private void renderRowTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int relX = mouseX - this.leftPos;
        int relY = mouseY - this.topPos;
        if (relX < LABEL_X || relX >= MINUS_X || relY < HEADER_H) {
            return;
        }
        int index = (relY - HEADER_H) / ROW_H;
        if (index < 0 || index >= this.menu.rowCount()) {
            return;
        }
        SpawnerConfigStats.Row row = this.menu.rows().get(index);
        List<FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(Language.getInstance().getVisualOrder(row.stat.name().copy().withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Language.getInstance().getVisualOrder(row.stat.desc().copy().withStyle(ChatFormatting.GRAY)));
        // 客户端方块实体已随数据包同步,能给出一条"当前值"的说明
        if (this.minecraft != null && this.minecraft.level != null
            && this.minecraft.level.getBlockEntity(this.menu.pos()) instanceof ApothSpawnerTile tile) {
            try {
                tooltip.add(Language.getInstance().getVisualOrder(row.stat.getTooltip(tile)));
            }
            catch (Throwable ignored) {
                // 拿不到就只显示名称与说明
            }
        }
        graphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int relX = (int) mouseX - this.leftPos;
        int relY = (int) mouseY - this.topPos;
        if (relY >= HEADER_H) {
            int index = (relY - HEADER_H) / ROW_H;
            if (index >= 0 && index < this.menu.rowCount()) {
                int action = this.actionAt(index, relX, button);
                if (action >= 0) {
                    if (this.minecraft != null && this.minecraft.gameMode != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                            index * SpawnerConfigStats.ACTIONS + action);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 命中哪个按钮:左键 -/+ 走一步,右键走十步,布尔项整条都是切换。 */
    private int actionAt(int index, int relX, int button) {
        if (this.menu.isBool(index)) {
            return relX >= MINUS_X && relX < this.imageWidth - 6 ? 2 : -1;
        }
        if (relX >= MINUS_X && relX < MINUS_X + BTN_W) {
            return button == 1 ? 2 : 0;
        }
        if (relX >= PLUS_X && relX < PLUS_X + BTN_W) {
            return button == 1 ? 3 : 1;
        }
        return -1;
    }

    private void drawButton(GuiGraphics graphics, int x1, int y1, int x2, int y2, String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
        graphics.fill(x1, y1, x2, y2, hovered ? 0xFF6E6E6E : 0xFF3C3C3C);
        graphics.fill(x1, y1, x2, y1 + 1, 0xFF9A9A9A);
        graphics.fill(x1, y2 - 1, x2, y2, 0xFF9A9A9A);
        graphics.fill(x1, y1, x1 + 1, y2, 0xFF9A9A9A);
        graphics.fill(x2 - 1, y1, x2, y2, 0xFF9A9A9A);
        graphics.drawCenteredString(this.font, label, (x1 + x2) / 2, y1 + 2, 0xFFFFFF);
    }
}
