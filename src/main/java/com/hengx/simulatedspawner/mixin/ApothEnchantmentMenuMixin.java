package com.hengx.simulatedspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.EnchantLockHandler;

import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu;
import net.minecraft.world.entity.player.Player;

/**
 * 神化自己的附魔台菜单({@code ApothEnchantmentMenu})<b>重写了 clickMenuButton</b>,
 * 因此原版 EnchantmentMenu 上的注入对它无效;这里单独注入一次,
 * 让"附魔已锁定"的装备在神化附魔台上同样只提升已有附魔等级。
 * 目标方法为 SRG 名 m_6366_,故 remap = false。
 */
@Mixin(ApothEnchantmentMenu.class)
public class ApothEnchantmentMenuMixin {

    @Inject(method = "m_6366_", at = @At("HEAD"), cancellable = true, remap = false)
    private void simulatedspawner$upgradeLockedEnchantments(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (EnchantLockHandler.tryUpgradeLocked((net.minecraft.world.inventory.EnchantmentMenu) (Object) this, player, id)) {
            cir.setReturnValue(true);
        }
    }
}
