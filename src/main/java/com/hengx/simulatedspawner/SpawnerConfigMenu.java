package com.hengx.simulatedspawner;

import java.util.List;

import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerTile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 创造模式刷怪笼配置界面的菜单。
 * <p>
 * 界面本身没有物品槽位,所有数值都通过 {@link ContainerData} 同步:
 * 服务端 {@code get} 直接读方块实体的实时值,客户端 {@code set}/{@code get} 用本地缓存,
 * 这样每 tick 的 {@code broadcastChanges} 会把变化推给客户端,界面自动刷新。
 * <p>
 * 点击通过原版菜单按钮包传递(button id = 行号 * {@link SpawnerConfigStats#ACTIONS} + 动作),
 * 服务端在 {@link #clickMenuButton} 里再次校验"创造模式",不依赖客户端。
 */
public class SpawnerConfigMenu extends AbstractContainerMenu {

    private final List<SpawnerConfigStats.Row> rows;

    private final BlockPos pos;

    /** 客户端可能为 null(方块实体未加载),此时只依赖同步数据。 */
    private final ApothSpawnerTile tile;

    private final boolean serverSide;

    private final int[] cache;

    /** 客户端:服务端通过 IContainerFactory 传来方块坐标。 */
    public SpawnerConfigMenu(int windowId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(windowId, playerInventory, buf.readBlockPos());
    }

    public SpawnerConfigMenu(int windowId, Inventory playerInventory, BlockPos pos) {
        super(SimulatedSpawner.SPAWNER_CONFIG_MENU.get(), windowId);
        this.pos = pos;
        this.rows = SpawnerConfigStats.rows();
        this.cache = new int[this.rows.size() * 2];
        this.serverSide = !playerInventory.player.level().isClientSide();
        this.tile = playerInventory.player.level().getBlockEntity(pos) instanceof ApothSpawnerTile spawner ? spawner : null;
        this.addDataSlots(new ContainerData() {

            @Override
            public int getCount() {
                return SpawnerConfigMenu.this.cache.length;
            }

            @Override
            public int get(int index) {
                return SpawnerConfigMenu.this.readData(index);
            }

            @Override
            public void set(int index, int value) {
                if (index >= 0 && index < SpawnerConfigMenu.this.cache.length) {
                    SpawnerConfigMenu.this.cache[index] = value;
                }
            }
        });
    }

    public List<SpawnerConfigStats.Row> rows() {
        return this.rows;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public int rowCount() {
        return this.rows.size();
    }

    /** 某一行的当前值(布尔项为 0/1)。 */
    public int value(int index) {
        return this.readData(index * 2);
    }

    /** 某一行是否是布尔项。 */
    public boolean isBool(int index) {
        return this.readData(index * 2 + 1) != 0;
    }

    private int readData(int index) {
        if (!this.serverSide) {
            return index >= 0 && index < this.cache.length ? this.cache[index] : 0;
        }
        if (this.tile == null || index < 0 || index >= this.cache.length) {
            return 0;
        }
        SpawnerConfigStats.Row row = this.rows.get(index / 2);
        if (index % 2 == 1) {
            return SpawnerConfigStats.isBool(this.tile, row) ? 1 : 0;
        }
        return SpawnerConfigStats.read(this.tile, row);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= this.rows.size() * SpawnerConfigStats.ACTIONS) {
            return false;
        }
        // 服务端再校验一次:只有创造模式玩家能直接改刷怪笼
        if (!player.isCreative() || player.level().isClientSide() || this.tile == null) {
            return false;
        }
        int index = id / SpawnerConfigStats.ACTIONS;
        int action = id % SpawnerConfigStats.ACTIONS;
        if (!SpawnerConfigStats.write(this.tile, this.rows.get(index), action)) {
            return false;
        }
        // 立刻同步方块实体(让探针/HWYLA 等也看到新值)与菜单数据
        this.tile.setChanged();
        BlockState state = this.tile.getBlockState();
        player.level().sendBlockUpdated(this.pos, state, state, Block.UPDATE_ALL);
        this.broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.tile != null
            && player.level().getBlockEntity(this.pos) == this.tile
            && player.distanceToSqr(this.pos.getX() + 0.5D, this.pos.getY() + 0.5D, this.pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
