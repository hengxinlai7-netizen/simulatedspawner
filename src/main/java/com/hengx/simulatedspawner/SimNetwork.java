package com.hengx.simulatedspawner;

import java.util.function.Supplier;

import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerTile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 本模组唯一的网络通道:客户端请求打开刷怪笼配置界面。
 * <p>
 * 之所以需要它:配置界面用 <b>Ctrl + 右键</b> 触发,而 Ctrl 键状态原版不会同步到服务端
 * (shift 会同步,ctrl 不会),所以只能由客户端判定后发一个包上来;服务端收到后
 * 仍会自己再校验一次"创造模式 + 该位置确实是神化刷怪笼"。
 */
public final class SimNetwork {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
        .named(new ResourceLocation(SimulatedSpawner.MODID, "main"))
        .networkProtocolVersion(() -> VERSION)
        .clientAcceptedVersions(VERSION::equals)
        .serverAcceptedVersions(VERSION::equals)
        .simpleChannel();

    private SimNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(OpenSpawnerConfig.class, 0, net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
            .encoder(OpenSpawnerConfig::encode)
            .decoder(OpenSpawnerConfig::new)
            .consumerMainThread(OpenSpawnerConfig::handle)
            .add();
    }

    /**
     * 服务端:校验并打开刷怪笼配置界面。
     *
     * @return 是否真的打开了
     */
    public static boolean openConfig(ServerPlayer player, BlockPos pos) {
        if (!SimCommonConfig.CREATIVE_SPAWNER_CONFIG.get() || !player.isCreative()) {
            return false;
        }
        if (!(player.level().getBlockEntity(pos) instanceof ApothSpawnerTile)) {
            return false;
        }
        NetworkHooks.openScreen(player,
            new SimpleMenuProvider((windowId, inventory, p) -> new SpawnerConfigMenu(windowId, inventory, pos),
                Component.translatable("gui.simulatedspawner.spawner_config")),
            pos);
        return true;
    }

    /** 请求包:只带一个方块坐标。 */
    public static class OpenSpawnerConfig {

        private final BlockPos pos;

        public OpenSpawnerConfig(BlockPos pos) {
            this.pos = pos;
        }

        public OpenSpawnerConfig(FriendlyByteBuf buf) {
            this.pos = buf.readBlockPos();
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(this.pos);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            ServerPlayer player = context.getSender();
            if (player != null) {
                openConfig(player, this.pos);
            }
            context.setPacketHandled(true);
        }
    }
}
