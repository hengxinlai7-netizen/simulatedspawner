package com.hengx.simulatedspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.EnchantLockHandler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;

/**
 * 原版附魔台:对"附魔已锁定"的装备附魔时,不再获得新附魔,而是把已有附魔各提升 1 级
 * (上限由通用配置 lockedEnchantMaxLevel 决定,默认 10),并按原版规则消耗青金石与经验等级。
 * <p>
 * 神化有自己的附魔台菜单({@code ApothEnchantmentMenu})并重写了该方法,
 * 因此那里的处理见 {@link ApothEnchantmentMenuMixin}。
 * 目标方法在生产环境为 SRG 名 m_6366_,故 remap = false。
 */
@Mixin(EnchantmentMenu.class)
public class EnchantmentMenuMixin {

    @Inject(method = "m_6366_", at = @At("HEAD"), cancellable = true, remap = false)
    private void simulatedspawner$upgradeLockedEnchantments(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        EnchantmentMenu menu = (EnchantmentMenu) (Object) this;
        if (EnchantLockHandler.tryUpgradeLocked(menu, player, id)) {
            cir.setReturnValue(true);
        }
    }
}
