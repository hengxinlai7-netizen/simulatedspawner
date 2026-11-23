package com.hengx.simulatedspawner;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 附魔锁定:
 * <ul>
 * <li>在铁砧里用 <b>1 个铁锭</b> 与"带附魔的物品"合成,即可给该物品打上"附魔已锁定"标记(消耗 1 个铁锭、1 级经验);</li>
 * <li>锁定的装备在附魔台上附魔时,不再获得新附魔,而是把<b>已有附魔各提升 1 级</b>,上限由通用配置 {@code lockedEnchantMaxLevel} 决定(默认 10);</li>
 * <li>已达上限时不消耗经验与青金石。</li>
 * </ul>
 */
public class EnchantLockHandler {

    /** 锁定标记 NBT 键。 */
    public static final String LOCK_TAG = "simulatedspawner:enchant_lock";

    public static boolean isLocked(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(LOCK_TAG);
    }

    public static void lock(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(LOCK_TAG, true);
    }

    /** 铁砧"锁定后无法解锁"提示的翻译键。 */
    public static final String LOCK_NOTICE_KEY = "message.simulatedspawner.anvil_lock_warning";

    /** 提示文本:客户端有翻译时用翻译,取不到翻译时回退为中文原文(自检/服务端也能拿到同样文字)。 */
    public static Component lockNoticeText() {
        return Component.translatableWithFallback(LOCK_NOTICE_KEY, "锁定后无法解锁");
    }

    /**
     * 本次铁砧合成是否正好是"执行锁定"这一动作:左边是<b>带附魔且未锁定</b>的物品,
     * 结果是同一个<b>已锁定</b>物品。
     * <p>
     * 只有这一刻才返回提示文本;未锁定、物品没有附魔、或对已锁定物品反复合成都返回 {@code null},
     * 因此提示只会在"锁定发生的那一刻"出现,不会变成铁砧界面上常驻的一行字。
     */
    @Nullable
    public static Component lockNotice(ItemStack input, ItemStack output) {
        if (input.isEmpty() || output.isEmpty()) {
            return null;
        }
        if (isLocked(input) || !isLocked(output) || !hasEnchantments(input)) {
            return null;
        }
        return lockNoticeText();
    }

    private static final String TAG_ENCHANTS = "Enchantments";

    private static final String TAG_STORED_ENCHANTS = "StoredEnchantments";

    private static String enchantTagKey(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK) ? TAG_STORED_ENCHANTS : TAG_ENCHANTS;
    }

    /** 物品是否有附魔(直接读 NBT,避免被其它模组改写的 EnchantmentHelper 影响)。 */
    public static boolean hasEnchantments(ItemStack stack) {
        return !stack.isEmpty() && !stack.getOrCreateTag().getList(enchantTagKey(stack), net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty();
    }

    /**
     * 把物品上已有的每个附魔各提升 1 级(不超过 {@code maxLevel})。
     * <p>
     * 直接读写物品 NBT 的 Enchantments / StoredEnchantments,不走 EnchantmentHelper
     * ——部分模组(如 Apothic Infinite / 神化无限)会改写 EnchantmentHelper 的读写,走它会导致本功能失效。
     *
     * @return 是否有任何附魔被提升(全部已满级时返回 false)
     */
    public static boolean upgradeLockedEnchantments(ItemStack stack, int maxLevel) {
        if (stack.isEmpty()) {
            return false;
        }
        String key = enchantTagKey(stack);
        net.minecraft.nbt.ListTag list = stack.getOrCreateTag().getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            return false;
        }
        net.minecraft.nbt.ListTag updated = new net.minecraft.nbt.ListTag();
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            net.minecraft.nbt.CompoundTag entry = list.getCompound(i).copy();
            int current = entry.getShort("lvl");
            int target = Math.min(current + 1, maxLevel);
            if (target > current) {
                entry.putShort("lvl", (short) target);
                changed = true;
            }
            updated.add(entry);
        }
        if (changed) {
            stack.getOrCreateTag().put(key, updated);
        }
        return changed;
    }

    private static java.lang.reflect.Field costsField;

    /**
     * 附魔台点击处理(原版与神化附魔台共用):
     * 输入槽是"附魔已锁定"的装备时,把已有附魔各提升 1 级(上限由配置决定),
     * 并按原版规则消耗青金石与经验等级。
     *
     * @return true 表示已接管本次点击(应取消原版/神化的添加新附魔逻辑)
     */
    public static boolean tryUpgradeLocked(EnchantmentMenu menu, Player player, int id) {
        ItemStack stack = menu.getSlot(0).getItem();
        if (!isLocked(stack)) {
            return false;
        }
        if (SimDebug.enabled()) {
            SimulationLogic.diagnose("锁定附魔:进入@" + player.getName().getString(),
                "锁定附魔:附魔台点击 id={} 输入物品={} 已锁定=true", id, stack.getItem());
        }

        int[] costs = costs(menu);
        if (costs == null || id < 0 || id >= costs.length) {
            if (SimDebug.enabled()) {
                SimulationLogic.diagnose("锁定附魔:选项无效@" + player.getName().getString(),
                    "锁定附魔:读不到附魔台选项(costs={}, id={})", costs == null ? "null" : costs.length, id);
            }
            return false;
        }
        int cost = costs[id];
        if (cost <= 0) {
            if (SimDebug.enabled()) {
                SimulationLogic.diagnose("锁定附魔:消耗为0@" + player.getName().getString(),
                    "锁定附魔:该选项经验消耗为 {} (<=0),未处理。", cost);
            }
            return false;
        }

        boolean creative = player.getAbilities().instabuild;
        int lapisNeeded = id + 1;
        // 只在第 2 格确实是青金石时才消耗它(有些附魔台布局不同,避免误删物品)
        boolean lapisSlot = menu.getSlot(1).getItem().is(Items.LAPIS_LAZULI);
        if (!creative && lapisSlot) {
            if (player.experienceLevel < cost || menu.getSlot(1).getItem().getCount() < lapisNeeded) {
                if (SimDebug.enabled()) {
                    SimulationLogic.diagnose("锁定附魔:材料不足@" + player.getName().getString(),
                        "锁定附魔:等级 {}/{},青金石 {}/{} ,未处理。", player.experienceLevel, cost,
                        menu.getSlot(1).getItem().getCount(), lapisNeeded);
                }
                return false;
            }
        }
        else if (!creative && player.experienceLevel < cost) {
            if (SimDebug.enabled()) {
                SimulationLogic.diagnose("锁定附魔:等级不足@" + player.getName().getString(),
                    "锁定附魔:经验等级 {}/{} 不足,未处理。", player.experienceLevel, cost);
            }
            return false;
        }

        // 已全部满级时不消耗材料
        if (!upgradeLockedEnchantments(stack, SimCommonConfig.LOCKED_ENCHANT_MAX_LEVEL.get())) {
            if (SimDebug.enabled()) {
                SimulationLogic.diagnose("锁定附魔:已满级@" + player.getName().getString(),
                    "锁定附魔:{} 上的附魔都已达到上限 {} 级,未消耗材料。", stack.getItem(),
                    SimCommonConfig.LOCKED_ENCHANT_MAX_LEVEL.get());
            }
            return false;
        }

        if (!creative) {
            if (lapisSlot) {
                menu.getSlot(1).remove(lapisNeeded);
            }
            player.onEnchantmentPerformed(stack, cost);
        }
        menu.getSlot(0).setChanged();
        if (SimDebug.enabled()) {
            SimulationLogic.diagnose("锁定附魔:成功@" + player.getName().getString(),
                "锁定附魔:已把 {} 上的附魔各提升 1 级(消耗 {} 级经验{} )。", stack.getItem(), cost,
                lapisSlot ? " + " + lapisNeeded + " 个青金石" : "");
        }
        return true;
    }

    private static int[] costs(EnchantmentMenu menu) {
        try {
            if (costsField == null) {
                for (String name : new String[] { "costs", "f_39446_" }) {
                    try {
                        costsField = EnchantmentMenu.class.getDeclaredField(name);
                        costsField.setAccessible(true);
                        break;
                    }
                    catch (NoSuchFieldException ignored) {
                        // 尝试下一个名字
                    }
                }
            }
            return costsField == null ? null : (int[]) costsField.get(menu);
        }
        catch (Throwable t) {
            return null;
        }
    }

    /** 铁砧:带附魔的物品 + 1 个铁锭 → 锁定的同一物品。 */
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || !right.is(Items.IRON_INGOT) || isLocked(left)) {
            return;
        }
        if (!hasEnchantments(left)) {
            return;
        }
        ItemStack output = left.copy();
        lock(output);
        event.setOutput(output);
        event.setCost(1);
        event.setMaterialCost(1);
        if (SimDebug.enabled()) {
            SimulationLogic.diagnose("锁定附魔:铁砧@" + (event.getPlayer() == null ? "?" : event.getPlayer().getName().getString()),
                "锁定附魔:铁砧已生成锁定物品({} + 铁锭),请拿到附魔台点击附魔。", left.getItem());
        }
    }

    /** 物品提示:显示"附魔已锁定"与当前上限。 */
    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (isLocked(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("tooltip.simulatedspawner.enchant_locked",
                SimCommonConfig.LOCKED_ENCHANT_MAX_LEVEL.get()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
