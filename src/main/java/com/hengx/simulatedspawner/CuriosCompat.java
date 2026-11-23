package com.hengx.simulatedspawner;

import java.util.Map;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * 与 Curios 的对接(单独成类):
 * 仅当 Curios 存在时才会被类加载,因此本模组不硬依赖 Curios。
 * 用途:模拟击杀需要临时给假玩家"戴"上七咒之戒,让神秘遗物的七咒之戒独特掉落判定通过。
 */
public final class CuriosCompat {

    /** 记录被占用的饰品槽,便于还原。 */
    private record SlotRef(ICuriosItemHandler handler, String type, int index, ItemStack previous) {}

    private CuriosCompat() {}

    /**
     * 把物品放进第一个空的饰品槽。
     *
     * @return 可用于 {@link #restore(Object)} 的句柄;失败返回 null
     */
    public static Object equipInEmptySlot(LivingEntity entity, ItemStack stack) {
        try {
            LazyOptional<ICuriosItemHandler> optional = CuriosApi.getCuriosInventory(entity);
            ICuriosItemHandler handler = optional.orElse(null);
            if (handler == null) {
                return null;
            }
            for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
                IDynamicStackHandler slots = entry.getValue().getStacks();
                for (int i = 0; i < slots.getSlots(); i++) {
                    if (slots.getStackInSlot(i).isEmpty()) {
                        ItemStack previous = slots.getStackInSlot(i).copy();
                        slots.setStackInSlot(i, stack.copy());
                        return new SlotRef(handler, entry.getKey(), i, previous);
                    }
                }
            }
        }
        catch (Throwable t) {
            SimulatedSpawner.LOGGER.debug("放置饰品失败(不影响模拟击杀主流程)", t);
        }
        return null;
    }

    /** 还原 {@link #equipInEmptySlot} 占用的槽位。 */
    public static void restore(Object handle) {
        if (!(handle instanceof SlotRef ref)) {
            return;
        }
        try {
            ICurioStacksHandler stacksHandler = ref.handler().getStacksHandler(ref.type()).orElse(null);
            if (stacksHandler != null) {
                stacksHandler.getStacks().setStackInSlot(ref.index(), ref.previous());
            }
        }
        catch (Throwable t) {
            SimulatedSpawner.LOGGER.debug("还原饰品槽失败", t);
        }
    }
}
