package com.hengx.simulatedspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.simulatedspawner.UpgradeContext;

import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 在神化刷怪笼方块被右击时,把交互玩家暂存到 {@link UpgradeContext},
 * 使我们的刷怪笼 stat 能在"两种模拟升级互斥"时把材料返还给该玩家。
 * <p>
 * 创造模式的"配置界面"不在这里处理:Ctrl 键状态原版不会同步到服务端,
 * 因此改由客户端 {@code ClientInteractions} 判定后发包请求(见 {@code SimNetwork})。
 * 该方法在开发环境名为 use,生产环境为 SRG 名,故通过 refmap 映射。
 */
@Mixin(ApothSpawnerBlock.class)
public class ApothSpawnerBlockMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void simulatedspawner$capturePlayer(BlockState state, Level level, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        UpgradeContext.set(player instanceof ServerPlayer serverPlayer ? serverPlayer : null);
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void simulatedspawner$releasePlayer(BlockState state, Level level, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        UpgradeContext.clear();
    }
}
