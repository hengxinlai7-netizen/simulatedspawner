package com.hengx.simulatedspawner;

import com.mojang.blaze3d.platform.InputConstants;

import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端交互:创造模式下 <b>Ctrl + 右键</b> 神化刷怪笼 -> 请求服务端打开配置界面。
 * <p>
 * Ctrl 键状态不会同步到服务端,所以判定在客户端做(直接查 GLFW 按键状态),
 * 之后发一个 {@link SimNetwork.OpenSpawnerConfig} 包;取消原版交互避免同时触发材料升级。
 */
@Mod.EventBusSubscriber(modid = SimulatedSpawner.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientInteractions {

    private ClientInteractions() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!SimCommonConfig.CREATIVE_SPAWNER_CONFIG.get()) {
            return;
        }
        if (!(event.getEntity() instanceof LocalPlayer player) || !player.isCreative()) {
            return;
        }
        if (!isCtrlDown()) {
            return;
        }
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ApothSpawnerBlock)) {
            return;
        }
        SimNetwork.CHANNEL.sendToServer(new SimNetwork.OpenSpawnerConfig(event.getPos()));
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Ctrl(左右任一)是否按下。 */
    private static boolean isCtrlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
            || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL);
    }
}
