package com.hengx.simulatedspawner.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.BeaconRollFlag;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraftforge.common.ForgeHooks;

/**
 * 维护信标模式的 GLM 深度计数:
 * 只有最外层的 modifyLoot(模拟击杀自己发起的调用)会绕过 GLM 条件,
 * GLM 内部嵌套掷取其它掉落表时按正常条件判定,避免第三方 GLM 崩溃。
 * Forge 自身类,方法名不重映射,故 remap = false;modifyLoot 为静态方法。
 */
@Mixin(ForgeHooks.class)
public class ForgeHooksMixin {

    @Inject(method = "modifyLoot(Ljava/util/List;Lnet/minecraft/world/level/storage/loot/LootContext;)Ljava/util/List;",
        at = @At("HEAD"), remap = false)
    private static void simulatedspawner$enterGlm(List<ItemStack> loot, LootContext context, CallbackInfoReturnable<List<ItemStack>> cir) {
        BeaconRollFlag.enterGlm();
    }

    @Inject(method = "modifyLoot(Ljava/util/List;Lnet/minecraft/world/level/storage/loot/LootContext;)Ljava/util/List;",
        at = @At("TAIL"), remap = false)
    private static void simulatedspawner$exitGlm(List<ItemStack> loot, LootContext context, CallbackInfoReturnable<List<ItemStack>> cir) {
        BeaconRollFlag.exitGlm();
    }

    @Inject(method = "modifyLoot(Lnet/minecraft/resources/ResourceLocation;Lit/unimi/dsi/fastutil/objects/ObjectArrayList;Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
        at = @At("HEAD"), remap = false)
    private static void simulatedspawner$enterGlm2(ResourceLocation id, ObjectArrayList<ItemStack> loot, LootContext context, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        BeaconRollFlag.enterGlm();
    }

    @Inject(method = "modifyLoot(Lnet/minecraft/resources/ResourceLocation;Lit/unimi/dsi/fastutil/objects/ObjectArrayList;Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
        at = @At("TAIL"), remap = false)
    private static void simulatedspawner$exitGlm2(ResourceLocation id, ObjectArrayList<ItemStack> loot, LootContext context, CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir) {
        BeaconRollFlag.exitGlm();
    }
}
