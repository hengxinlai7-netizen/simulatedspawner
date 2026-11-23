package com.hengx.simulatedspawner.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.AffixPoolDiag;
import com.hengx.simulatedspawner.AffixToggle;

import dev.shadowsoffire.apotheosis.adventure.affix.Affix;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixInstance;
import dev.shadowsoffire.apotheosis.adventure.affix.augmenting.AugmentingMenu;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 强化台菜单:处理来自界面的"词缀开关"按钮(原版菜单按钮包,button id = 64 + 词条序号)。
 * 目标方法 m_6366_ 是原版 AbstractContainerMenu#clickMenuButton 的重写,生产环境为 SRG 名,故 remap = false。
 */
@Mixin(AugmentingMenu.class)
public class AugmentingMenuMixin {

    @Inject(method = "m_6366_", at = @At("HEAD"), cancellable = true, remap = false)
    private void simulatedspawner$toggleAffix(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        // 诊断:点"刷新"时把血战能否进入候选池的原因打到日志里。默认关闭(debugLog=false),
        // 关闭时这里只做一次 volatile 判断,连 getMainItem() 都不调用。
        if (id == AugmentingMenu.REROLL && com.hengx.simulatedspawner.SimDebug.enabled()) {
            AffixPoolDiag.onReroll(((AugmentingMenu) (Object) this).getMainItem());
        }
        if (id < AffixToggle.TOGGLE_BUTTON_BASE) {
            return;
        }
        AugmentingMenu menu = (AugmentingMenu) (Object) this;
        ItemStack item = menu.getMainItem();
        if (item.isEmpty()) {
            return;
        }
        List<AffixInstance> affixes = AffixToggle.itemAffixes(item);
        int index = id - AffixToggle.TOGGLE_BUTTON_BASE;
        if (index < 0 || index >= affixes.size()) {
            return;
        }
        ResourceLocation affixId = AffixToggle.affixId(affixes.get(index));
        if (affixId == null) {
            return;
        }
        AffixToggle.setDisabled(item, affixId, !AffixToggle.isDisabled(item, affixId));
        cir.setReturnValue(true);
    }
}
