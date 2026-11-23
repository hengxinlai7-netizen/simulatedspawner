package com.hengx.simulatedspawner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hengx.simulatedspawner.SimCommonConfig;
import com.hengx.simulatedspawner.SimulatedAccess;
import com.hengx.simulatedspawner.SimulationLogic;

import dev.shadowsoffire.apotheosis.spawn.spawner.ApothSpawnerTile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * 在神化刷怪笼的刷怪循环里拦截"把生物加入世界"这一步:
 * <p>
 * - 模拟模式下:不生成生物,改为掷"玩家击杀"掉落并送入上方容器;
 * 返回 true 表示本次生成成功(掉落已入容器),刷怪笼按 min/max delay 正常继续;
 * 返回 false 表示上方无容器/空间不足,神化原逻辑会重新计时,刷怪笼暂停运行。
 * - 非模拟模式:原样调用,完全不影响神化刷怪笼的任何行为。
 * <p>
 * 由于只替换"生成生物"这一步,神化的激活检查(玩家距离、红石控制、光照/条件等)
 * 与 min/max delay、spawn_count 等升级全部原样生效,每周期掷出 spawn_count 次掉落。
 * <p>
 * 另外两个重定向处理"生物装不下就没有产物":神化在真正生成生物之前会先做两次
 * "这个生物的体积放得下吗"检查(见 {@link #simulatedspawner$skipFitCheck} /
 * {@link #simulatedspawner$skipObstructionCheck}),模拟模式其实不生成生物,
 * 体积检查没有意义,但因为拦在"生成生物"这一步之前,一旦不通过整轮就不会有掉落。
 */
@Mixin(ApothSpawnerTile.SpawnerLogicExt.class)
public class SpawnerLogicExtMixin {

    @Redirect(method = "serverTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;tryAddFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean simulatedspawner$redirectSpawn(ServerLevel level, Entity entity) {
        BaseSpawner self = (BaseSpawner) (Object) this;
        BlockEntity blockEntity = self.getSpawnerBlockEntity();

        if (blockEntity instanceof ApothSpawnerTile tile
            && blockEntity instanceof SimulatedAccess access) {
            if (access.simulatedspawner$isFishing()) {
                return SimulationLogic.simulateFishing(level, tile.getBlockPos(),
                    access.simulatedspawner$getBonusLevel(), access.simulatedspawner$isBeacon());
            }
            if (access.simulatedspawner$isSimulated()) {
                return SimulationLogic.simulateSpawn(level, tile.getBlockPos(), entity,
                    access.simulatedspawner$getBonusLevel(), access.simulatedspawner$isBeacon(),
                    access.simulatedspawner$isRevelation());
            }
            if (com.hengx.simulatedspawner.SimDebug.enabled()) {
                SimulationLogic.diagnose("未启用@" + tile.getBlockPos(),
                    "刷怪笼 {} 未处于模拟模式(模拟掉落={}, 模拟钓鱼={}),按正常刷怪处理。若你已用升级材料右击过,请确认刷怪笼物品提示里能看到对应词条。",
                    tile.getBlockPos(), access.simulatedspawner$isSimulated(), access.simulatedspawner$isFishing());
            }
        }

        return level.tryAddFreshEntityWithPassengers(entity);
    }

    /**
     * 配置 disableSpawnerParticles=true 时,禁用刷怪笼刷怪时的爆裂粒子。
     */
    @Redirect(method = "serverTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;levelEvent(ILnet/minecraft/core/BlockPos;I)V"))
    private void simulatedspawner$redirectSpawnParticles(ServerLevel level, int type, BlockPos pos, int data) {
        if (!SimCommonConfig.DISABLE_SPAWNER_PARTICLES.get()) {
            level.levelEvent(type, pos, data);
        }
    }

    /**
     * 配置 disableSpawnerParticles=true 时,禁用刷怪笼刷出生物时的出生动画粒子。
     */
    @Redirect(method = "serverTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Mob;spawnAnim()V"))
    private void simulatedspawner$redirectSpawnAnim(Mob mob) {
        if (!SimCommonConfig.DISABLE_SPAWNER_PARTICLES.get()) {
            mob.spawnAnim();
        }
    }

    /**
     * 模拟/钓鱼模式下跳过"生物体积是否放得下"的碰撞检查(神化 serverTick 里的第一道检查):
     * <pre>
     * if (!level.noCollision(entityType.getAABB(x, y, z))) continue;   // 字节码偏移 254~269
     * </pre>
     * 这个 AABB 是<b>生物自己的碰撞箱</b>,位置又落在刷怪笼周围 ±spawn_range 内:
     * spawn_range 小的时候生物必定和刷怪笼方块本身相交(方块占 1x1x1),体型越大越容易整轮不通过,
     * 于是 {@code continue} 掉,连生物实体都不会创建 —— 我们拦截"加入世界"的代码根本没机会执行,
     * 表现就是"模拟击杀没有产物"。模拟模式不生成生物,这道检查可以安全跳过。
     */
    @Redirect(method = "serverTick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean simulatedspawner$skipFitCheck(ServerLevel level, AABB box) {
        return simulatedspawner$isSimulatedMode() || level.noCollision(box);
    }

    /**
     * 模拟/钓鱼模式下跳过第二道检查(神化 checkSpawnPositionSpawner 里的
     * {@code mob.checkSpawnObstruction(level)}:液体/实体阻挡),同理 —— 不生成生物就没有"挡住"这回事。
     * <p>
     * 这里 {@code remap = false}:{@code checkSpawnPositionSpawner} 是神化自己的方法,
     * 在生产环境里没有 SRG 名(混淆映射里查不到),必须关掉重映射并按 SRG 名写目标方法
     * (和 {@code ServerLevelMixin} 同一套写法)。
     */
    @Redirect(method = "checkSpawnPositionSpawner", remap = false, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Mob;m_6914_(Lnet/minecraft/world/level/LevelReader;)Z"))
    private boolean simulatedspawner$skipObstructionCheck(Mob mob, LevelReader level) {
        return simulatedspawner$isSimulatedMode() || mob.checkSpawnObstruction(level);
    }

    /** 这个刷怪笼当前是否处于"模拟掉落/模拟钓鱼"模式。 */
    @Unique
    private boolean simulatedspawner$isSimulatedMode() {
        BlockEntity blockEntity = ((BaseSpawner) (Object) this).getSpawnerBlockEntity();
        if (blockEntity instanceof SimulatedAccess access) {
            return access.simulatedspawner$isSimulated() || access.simulatedspawner$isFishing();
        }
        return false;
    }
}
