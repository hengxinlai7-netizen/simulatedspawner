package com.hengx.simulatedspawner.mixin;

import java.util.Comparator;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import dev.shadowsoffire.apotheosis.spawn.compat.SpawnerJEIPlugin;
import dev.shadowsoffire.apotheosis.spawn.modifiers.SpawnerModifier;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 修复神化 SpawnerJEIPlugin 里违反排序契约的比较器:
 * 原 lambda 在"两个配方都带副手材料"时互相返回 1(不满足反对称性),
 * 只要出现多个带副手材料的配方(例如本模组 4 个石英反向配方)就会触发
 * TimSort 的 "Comparison method violates its general contract" 崩溃,
 * 导致 JEI 中所有刷怪笼升级配方都不显示。
 * 这里替换为: 无副手的配方在前, 相同者按配方 ID 排序(全序, 契约安全)。
 * JEI 仅存在于客户端, 故本 Mixin 放在 client 段;目标为模组类, remap = false。
 */
@Mixin(SpawnerJEIPlugin.class)
public class SpawnerJEIPluginMixin {

    @Redirect(method = "registerRecipes", at = @At(value = "INVOKE",
        target = "Ljava/util/List;sort(Ljava/util/Comparator;)V", remap = false),
        remap = false)
    private void simulatedspawner$fixSort(List<SpawnerModifier> recipes, Comparator<SpawnerModifier> broken) {
        recipes.sort(Comparator
            .comparing((SpawnerModifier recipe) -> recipe.getOffhandInput() == Ingredient.EMPTY ? 0 : 1)
            .thenComparing(SpawnerModifier::getId));
    }
}
