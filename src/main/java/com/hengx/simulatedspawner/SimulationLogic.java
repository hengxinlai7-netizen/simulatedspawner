package com.hengx.simulatedspawner;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootTableReference;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * 模拟掉落 / 模拟钓鱼的全部核心逻辑:掷掉落表 -> 找上方容器 -> 全部放得下才写入。
 */
public final class SimulationLogic {

    /** 限制 GLM 异常日志条数,避免异常时把日志刷爆。 */
    private static final java.util.concurrent.atomic.AtomicInteger GLM_ERROR_LOGS = new java.util.concurrent.atomic.AtomicInteger();

    /** 已打印过的诊断 key,保证每种情况只提示一次(上限 64 条)。 */
    private static final Set<String> DIAGNOSED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 限频诊断日志:同一个 key 只打印一次,最多 64 条。
     * 用于让玩家日志能直接说明"为什么没有产出"。
     * <p>
     * 1.7.9 起默认<b>关闭</b>({@code general.debugLog=false}):第一行就查 {@link SimDebug#enabled()}
     * 这个 volatile 字段,关闭时立即返回,不做任何格式化、不占 {@link #DIAGNOSED} 名额。
     * 调用点另外还各自套了一层同样的判断,所以关闭时连 key 字符串都不会拼。
     */
    public static void diagnose(String key, String message, Object... args) {
        if (!SimDebug.enabled()) {
            return;
        }
        if (DIAGNOSED.size() < 64 && DIAGNOSED.add(key)) {
            SimulatedSpawner.LOGGER.warn("[诊断] " + message, args);
        }
    }

    // ---------------------------------------------------------------- 反射缓存
    // 每次模拟击杀都会走"自定义死亡掉落"与"全局战利品修改器枚举";原实现每次调用都做
    // Class.forName / getDeclaredMethod / setAccessible —— 这里解析一次后缓存,行为不变
    // (拿到的仍是同一个 Method,invoke 语义完全相同)。

    /** {@code LivingEntity#dropCustomDeathLoot} 的缓存(解析失败为 null)。 */
    private static volatile java.lang.reflect.Method CUSTOM_DROPS_CACHE;
    private static volatile boolean customDropsResolved;

    /** Forge 全局战利品修改器管理器的两个方法缓存(解析失败为 null)。 */
    private static volatile java.lang.reflect.Method GLM_GET_MANAGER_CACHE;
    private static volatile java.lang.reflect.Method GLM_GET_ALL_CACHE;
    private static volatile boolean glmResolved;

    private SimulationLogic() {}

    /**
     * 在神化刷怪笼把生物加入世界前被 Mixin 调用,替代"生成生物"这一步。
     *
     * @param bonusLevel 钻石块增益等级:模拟击杀的抢夺(Looting)等级
     * @param beacon     信标:所有概率类特殊掉落必定掉落
     * @param revelation 启示之证:模拟击杀时临时戴上七咒之戒,取得神秘遗物的"七咒之戒独特掉落"加成
     * @return true  = 本次"生成"成功(掉落已全部入容器,或该生物本就没有掉落),刷怪笼按正常节奏继续;
     *         false = 上方没有容器 / 容器空间不足,刷怪笼停止本次运行,重新计时后重试。
     */
    public static boolean simulateSpawn(Level level, BlockPos spawnerPos, Entity entity, int bonusLevel, boolean beacon, boolean revelation) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        // 上方必须先有可用容器,否则刷怪笼直接停止运行
        IItemHandler handler = findContainerAbove(serverLevel, spawnerPos);
        if (handler == null) {
            if (SimDebug.enabled()) {
                diagnose("容器@" + spawnerPos,
                    "刷怪笼 {} 上方 {} 格内没有找到带物品栏的容器,模拟已停止。请把箱子/木桶等容器放在刷怪笼正上方。",
                    spawnerPos, SimConfig.CONTAINER_SEARCH_RANGE.get());
            }
            return false;
        }

        if (SimDebug.enabled()) {
            diagnose("模式@" + spawnerPos, "刷怪笼 {} 模拟击杀已生效(信标={}, 启示之证={}, 增益等级={})",
                spawnerPos, beacon, revelation, bonusLevel);
        }

        // 模拟击杀的完整流程:
        //  1) 掷该生物自身的掉落表(实体可重写,阶段不同表也不同,如戈蒂使徒);
        //  2) 触发其"自定义死亡掉落"(模组代码里 spawnAtLocation 的特殊掉落,如使徒的不洁之血);
        //  3) 触发玩家击杀掉落事件(LivingDropsEvent),模组基于事件的额外掉落(钱币/首领掉落等)会进列表;
        //  4) 捕获本窗口内被直接生成到世界里的掉落实体,改为收进产物(避免钱币之类在刷怪笼周围飞溅)。
        beginItemCapture(spawnerPos);
        List<ItemStack> loot = new java.util.ArrayList<>();
        try {
            loot.addAll(rollPlayerKillLoot(serverLevel, entity, bonusLevel, beacon));
            if (entity instanceof LivingEntity living) {
                invokeCustomDeathDrops(living, serverLevel);
            }
            loot = firePlayerKillDropEvents(serverLevel, entity, loot, revelation);
        }
        catch (Throwable t) {
            if (SimDebug.enabled()) {
                diagnose("模拟失败@" + entity.getType(),
                    "模拟击杀处理时出错({}),已按基础掉落处理。", t.getClass().getSimpleName());
            }
        }
        finally {
            loot.addAll(endItemCapture());
        }

        if (loot.isEmpty()) {
            // 该生物没有掉落:视作一次成功的"生成",保持刷怪笼原有节奏
            if (SimDebug.enabled()) {
                diagnose("无掉落@" + entity.getType(),
                    "模拟击杀:{} 掷取掉落表并应用全部战利品修改器后仍为空,本轮无产出。该生物在玩家击杀下确实没有掉落(如原版蝙蝠/蜜蜂/青蛙/嗅探兽);信标模式可放宽条件,但仍需该生物有对应掉落条目或战利品修改器会注入掉落。",
                    entity.getType());
            }
            return true;
        }

        if (!tryInsertAll(handler, loot)) {
            if (SimDebug.enabled()) {
                diagnose("容器满@" + spawnerPos,
                    "刷怪笼 {} 上方容器空间不足或拒绝接收物品,模拟已停止。腾出空间后会自动继续。", spawnerPos);
            }
            return false;
        }

        // 视觉反馈:与正常刷出生物时相同的粒子(可在通用配置中禁用)
        if (!SimCommonConfig.DISABLE_SPAWNER_PARTICLES.get()) {
            serverLevel.levelEvent(LevelEvent.PARTICLES_MOBBLOCK_SPAWN, spawnerPos, 0);
        }
        return true;
    }

    /** 物品捕获窗口的半径平方(以刷怪笼为中心),只收集这个范围内的掉落实体。 */
    private static final double CAPTURE_RADIUS_SQR = 64.0D;

    private static boolean captureActive;

    @Nullable
    private static List<ItemStack> captureBuffer;

    @Nullable
    private static Vec3 captureCenter;

    /** 开始捕获"被直接生成到世界里的掉落物"。 */
    private static void beginItemCapture(BlockPos center) {
        captureActive = true;
        captureBuffer = new java.util.ArrayList<>();
        captureCenter = Vec3.atCenterOf(center);
    }

    /** 结束捕获并返回收集到的物品。 */
    private static List<ItemStack> endItemCapture() {
        List<ItemStack> result = captureBuffer == null ? List.<ItemStack>of() : captureBuffer;
        captureActive = false;
        captureBuffer = null;
        captureCenter = null;
        return result;
    }

    /**
     * 由 {@code ServerLevelMixin} 调用:模拟击杀窗口内被直接加入世界的掉落实体,
     * 改为收集进产物(模组的掉币、自定义死亡掉落等都走这条路),
     * 避免它们在刷怪笼周围飞溅。
     *
     * @return true 表示已收集,应取消这次加入世界
     */
    public static boolean tryCaptureItemEntity(ItemEntity item) {
        if (!captureActive || captureBuffer == null) {
            return false;
        }
        if (captureCenter != null && item.position().distanceToSqr(captureCenter) > CAPTURE_RADIUS_SQR) {
            return false;
        }
        ItemStack stack = item.getItem();
        if (stack.isEmpty()) {
            return false;
        }
        captureBuffer.add(stack.copy());
        return true;
    }

    /**
     * 触发实体的"自定义死亡掉落"(原版受保护方法 {@code dropCustomDeathLoot}):
     * 模组常在这里用 spawnAtLocation 掉特殊物品(例如戈蒂使徒的不洁之血)。
     * 这些物品以掉落实体形式进入世界,会被捕获窗口收进产物;失败只记调试日志。
     */
    private static void invokeCustomDeathDrops(LivingEntity living, ServerLevel level) {
        try {
            java.lang.reflect.Method target = CUSTOM_DROPS_CACHE;
            if (!customDropsResolved) {
                customDropsResolved = true;
                for (String name : new String[] { "dropCustomDeathLoot", "m_7472_" }) {
                    try {
                        target = LivingEntity.class.getDeclaredMethod(name, DamageSource.class, int.class, boolean.class);
                        target.setAccessible(true);
                        break;
                    }
                    catch (NoSuchMethodException ignored) {
                        // 尝试下一个名字
                    }
                }
                CUSTOM_DROPS_CACHE = target;
            }
            if (target == null) {
                return;
            }
            ServerPlayer fakePlayer = FakePlayerFactory.getMinecraft(level);
            DamageSource source = level.damageSources().playerAttack(fakePlayer);
            target.invoke(living, source, SimConfig.SIMULATED_LOOTING.get(), true);
        }
        catch (Throwable t) {
            SimulatedSpawner.LOGGER.debug("调用自定义死亡掉落失败", t);
        }
    }

    /**
     * 触发一次玩家击杀掉落事件({@code LivingDropsEvent}),模组基于该事件的额外掉落会并入产物:
     * <ul>
     * <li>事件处理器直接在传入的掉落列表上增删,因此返回事件处理后的完整列表;</li>
     * <li>启示之证模式下额外给假玩家戴上七咒之戒,使神秘遗物的"七咒之戒独特掉落"判定通过;</li>
     * <li>任意一步失败或事件把掉落清空都退回原掉落,绝不让掉落弄丢。</li>
     * </ul>
     */
    private static List<ItemStack> firePlayerKillDropEvents(ServerLevel level, Entity entity, List<ItemStack> base, boolean revelation) {
        if (!(entity instanceof LivingEntity living)) {
            return base;
        }

        ServerPlayer fakePlayer = FakePlayerFactory.getMinecraft(level);
        Object equipped = null;
        if (revelation && ModList.get().isLoaded("enigmaticlegacy")) {
            Item ring = BuiltInRegistries.ITEM.get(new ResourceLocation("enigmaticlegacy", "cursed_ring"));
            if (ring != Items.AIR && ModList.get().isLoaded("curios")) {
                equipped = CuriosCompat.equipInEmptySlot(fakePlayer, new ItemStack(ring));
                if (equipped == null) {
                    if (SimDebug.enabled()) {
                        diagnose("启示饰品失败", "启示之证:没能把七咒之戒放进假玩家的饰品栏,本次未提供七咒之戒掉落加成。");
                    }
                }
            }
        }

        try {
            DamageSource source = level.damageSources().playerAttack(fakePlayer);
            List<ItemEntity> dropEntities = new java.util.ArrayList<>(base.size());
            for (ItemStack stack : base) {
                if (!stack.isEmpty()) {
                    dropEntities.add(new ItemEntity(level, living.getX(), living.getY(), living.getZ(), stack.copy()));
                }
            }

            ForgeHooks.onLivingDrops(living, source, dropEntities, SimConfig.SIMULATED_LOOTING.get(), true);

            List<ItemStack> result = new java.util.ArrayList<>(dropEntities.size());
            for (ItemEntity dropped : dropEntities) {
                if (dropped != null && !dropped.getItem().isEmpty()) {
                    result.add(dropped.getItem().copy());
                }
            }
            if (result.isEmpty() && !base.isEmpty()) {
                if (SimDebug.enabled()) {
                    diagnose("事件清空@" + entity.getType(),
                        "模拟击杀:玩家击杀掉落事件处理后掉落被清空,已保留原掉落({} 件)。", base.size());
                }
                return base;
            }
            return result;
        }
        catch (Throwable t) {
            if (SimDebug.enabled()) {
                diagnose("事件失败@" + entity.getType(),
                    "模拟击杀:触发玩家击杀掉落事件时出错({}),已忽略该步骤。", t.getClass().getSimpleName());
            }
            return base;
        }
        finally {
            if (equipped != null) {
                CuriosCompat.restore(equipped);
            }
        }
    }

    /**
     * 掷出模拟击杀的掉落,并保证"每轮都有产出":
     * 原版不少生物掉落表的数量范围包含 0(僵尸腐肉、骷髅骨头/箭、凋灵骷髅骨头等),
     * 掷出 0 时物品堆会直接变空且物品信息随之丢失;若该轮其它池的条件又都没中,
     * 整轮产出就是 0 件,刷怪笼看起来就像"失效"。因此结果为空时重掷,最多 5 次。
     */
    private static List<ItemStack> rollPlayerKillLoot(ServerLevel level, Entity entity, int bonusLevel, boolean beacon) {
        for (int attempt = 0; attempt < 5; attempt++) {
            List<ItemStack> drops = rollPlayerKillLootOnce(level, entity, bonusLevel, beacon);
            if (!drops.isEmpty()) {
                return drops;
            }
        }
        return List.of();
    }

    /**
     * 单次按"玩家击杀"规则掷出该生物的掉落表。
     * <p>
     * 假玩家的主手武器带抢夺附魔并作为 TOOL 参数,使掉落表中基于抢夺的加成生效
     * (信标开启时特殊条件全部通过)。
     * <p>
     * 钻石块增益加在<b>抢夺(Looting)</b>而不是时运(Fortune)上:
     * 原版生物掉落通过 {@code EnchantmentHelper.getMobLooting} 读取抢夺等级
     * ({@code random_chance_with_looting} 条件、{@code looting_enchant} 函数),
     * 而时运是给方块掉落用的,放在击杀上下文里对绝大多数生物掉落表毫无作用。
     */
    private static List<ItemStack> rollPlayerKillLootOnce(ServerLevel level, Entity entity, int bonusLevel, boolean beacon) {
        if (!(entity instanceof LivingEntity living)) {
            return List.of();
        }

        ServerPlayer fakePlayer = FakePlayerFactory.getMinecraft(level);

        ItemStack weapon = new ItemStack(Items.DIAMOND_SWORD);
        int looting = SimConfig.SIMULATED_LOOTING.get() + bonusLevel;
        if (looting > 0) {
            weapon.enchant(Enchantments.MOB_LOOTING, looting);
        }
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, weapon);

        DamageSource source = level.damageSources().playerAttack(fakePlayer);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, living)
            .withParameter(LootContextParams.ORIGIN, living.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, source)
            .withParameter(LootContextParams.KILLER_ENTITY, fakePlayer)
            .withParameter(LootContextParams.DIRECT_KILLER_ENTITY, fakePlayer)
            .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, fakePlayer)
            .withParameter(LootContextParams.TOOL, weapon)
            .withLuck(SimConfig.SIMULATED_LUCK.get().floatValue())
            .create(LootContextParamSets.ENTITY);

        // 用实体自身的掉落表:不少模组会重写它(按阶段/形态返回不同表,例如戈蒂使徒),
        // 用 EntityType 的静态表会取到错误的那张。
        ResourceLocation tableId = living.getLootTable();
        LootTable table = level.getServer().getLootData().getLootTable(tableId);

        // 两种模式都走"基础掷取 + 受保护的 GLM 链":
        //  - 基础掷取用 getRandomItemsRaw(不跑 GLM),自己控制 GLM 应用过程;
        //  - GLM 逐个应用,遇到"把掉落清空"或抛异常的修改器只跳过它、保留其它产出。
        //    包内某模组(如妖怪们的归家 RemoveItemModifier)会把大量生物的原版掉落整表删除,
        //    不做保护时普通模式同样会出现"某些生物没有掉落"。
        //  注意不能用 Forge 的两参数 modifyLoot:它会 list.toArray() 后强转 ItemStack[],
        //  对任何列表都会抛 ClassCastException(47.2.20 实测)。
        if (beacon) {
            BeaconRollFlag.setActive(true);
            try {
                return rollBaseAndModifiers(table, params, tableId, true);
            }
            finally {
                BeaconRollFlag.resetDepth();
                BeaconRollFlag.setActive(false);
            }
        }
        return rollBaseAndModifiers(table, params, tableId, false);
    }

    /**
     * 真实击杀场景下额外掷一次掉落表(清道夫"一击必掉双倍"用):
     * 用真实击杀者与真实伤害来源作为上下文,不做"为空重掷"的兜底,
     * 结果只作为额外产出附加到事件掉落里,与神化清道夫自身的概率效果相互独立。
     */
    public static List<ItemStack> rollExtraKillDrops(LivingEntity killed, DamageSource source, Entity killer, int looting) {
        if (!(killed.level() instanceof ServerLevel level)) {
            return List.of();
        }
        ItemStack tool = killer instanceof LivingEntity livingKiller ? livingKiller.getMainHandItem() : ItemStack.EMPTY;
        LootParams.Builder builder = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, killed)
            .withParameter(LootContextParams.ORIGIN, killed.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, source)
            .withParameter(LootContextParams.KILLER_ENTITY, killer)
            .withParameter(LootContextParams.DIRECT_KILLER_ENTITY, killer)
            .withParameter(LootContextParams.TOOL, tool);
        if (killer instanceof ServerPlayer serverPlayer) {
            builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, serverPlayer);
        }
        LootParams params = builder.create(LootContextParamSets.ENTITY);

        ResourceLocation tableId = killed.getLootTable();
        LootTable table = level.getServer().getLootData().getLootTable(tableId);
        // 真实击杀场景(清道夫额外掷取):永远按真实击杀处理,不吃信标模式的放宽
        return rollBaseAndModifiers(table, params, tableId, false);
    }

    /** 基础掉落掷取 + GLM 链。 */
    private static List<ItemStack> rollBaseAndModifiers(LootTable table, LootParams params, ResourceLocation tableId, boolean beacon) {
        ObjectArrayList<ItemStack> raw = new ObjectArrayList<>();
        table.getRandomItemsRaw(params, raw::add);

        List<ItemStack> base = normalizeDrops(raw);

        // 注意:即使基础掷取为空也必须继续跑 GLM。
        // 大量模组是往"本身没有掉落条目"的掉落表里"注入"掉落的(例如包内的
        // wizard_terra_cuiros:entities/bat 与 confluence:entities/bat 都挂在原版蝙蝠的掉落表上),
        // 提前 return 会让这些注入的掉落永远不出现 —— 看上去就像"模拟击杀漏掉了模组掉落"。
        LootContext glmContext = new LootContext.Builder(params)
            .withQueriedLootTableId(tableId)
            .create(tableId);

        return applyLootModifiersSafely(tableId, base, glmContext, beacon);
    }

    /**
     * 逐个应用全局战利品修改器。
     * <p>
     * 普通模式:与真实击杀完全一致 —— 修改器返回什么就用什么(包括"把掉落清空"的修改器,
     * 包内 youkaishomecoming 就是用这种修改器拿掉原版掉落的;真实击杀同样拿不到,所以这里也不能替它保留)。
     * <p>
     * 信标模式:放宽条件,并且跳过"会把掉落清空"或抛异常的修改器,保证特殊掉落不丢。
     */
    private static List<ItemStack> applyLootModifiersSafely(ResourceLocation tableId, List<ItemStack> base, LootContext context, boolean beacon) {
        Iterable<?> mods = listLootModifiers();
        if (mods == null) {
            ObjectArrayList<ItemStack> probe = copyStacks(base);
            try {
                List<ItemStack> modified = normalizeDrops(ForgeHooks.modifyLoot(tableId, probe, context));
                if (!modified.isEmpty() || !beacon) {
                    return modified;
                }
            }
            catch (Exception ex) {
                if (GLM_ERROR_LOGS.incrementAndGet() <= 3) {
                    SimulatedSpawner.LOGGER.warn("应用全局战利品修改器时出错,已按基础掉落处理(后续同类错误不再重复输出)", ex);
                }
                else {
                    SimulatedSpawner.LOGGER.debug("应用全局战利品修改器时出错", ex);
                }
            }
            if (beacon && SimDebug.enabled()) {
                diagnose("GLM清空@" + tableId,
                    "信标模式:模组战利品修改器把 {} 的掉落清空了,已回退为基础掉落(基础掉落 {} 件)。",
                    tableId, base.size());
            }
            return base;
        }

        ObjectArrayList<ItemStack> current = copyStacks(base);
        for (Object mod : mods) {
            ObjectArrayList<ItemStack> probe = copyStacks(current);
            ObjectArrayList<ItemStack> result;
            try {
                result = ((net.minecraftforge.common.loot.IGlobalLootModifier) mod).apply(probe, context);
            }
            catch (Throwable t) {
                if (SimDebug.enabled()) {
                    diagnose("GLM异常@" + mod.getClass().getName(),
                        "战利品修改器 {} 执行出错,已跳过({})", mod.getClass().getName(), t.getClass().getSimpleName());
                }
                continue;
            }
            if (result == null) {
                continue;
            }
            if (result.isEmpty() && beacon) {
                // 仅信标模式:把掉落清空的修改器直接跳过,保留此前累积的掉落
                if (SimDebug.enabled()) {
                    diagnose("GLM清空源@" + mod.getClass().getName() + "@" + tableId,
                        "信标模式:战利品修改器 {} 会清空 {} 的掉落,已跳过该修改器(保留其它掉落)。",
                        mod.getClass().getName(), tableId);
                }
                continue;
            }
            current = result;
        }
        return normalizeDrops(current);
    }

    /** 反射获取 Forge 的全局战利品修改器列表;不可用时返回 null。 */
    private static Iterable<?> listLootModifiers() {
        try {
            java.lang.reflect.Method getManager = GLM_GET_MANAGER_CACHE;
            java.lang.reflect.Method getAll = GLM_GET_ALL_CACHE;
            if (!glmResolved) {
                glmResolved = true;
                try {
                    Class<?> handler = Class.forName("net.minecraftforge.common.ForgeInternalHandler");
                    getManager = handler.getDeclaredMethod("getLootModifierManager");
                    getManager.setAccessible(true);
                    Object probeManager = getManager.invoke(null);
                    getAll = probeManager.getClass().getMethod("getAllLootMods");
                    GLM_GET_MANAGER_CACHE = getManager;
                    GLM_GET_ALL_CACHE = getAll;
                }
                catch (Throwable t) {
                    GLM_GET_MANAGER_CACHE = null;
                    GLM_GET_ALL_CACHE = null;
                    SimulatedSpawner.LOGGER.debug("无法枚举全局战利品修改器,退回 Forge 链式处理", t);
                    return null;
                }
            }
            if (getManager == null || getAll == null) {
                return null;
            }
            Object manager = getManager.invoke(null);
            Object modsObj = getAll.invoke(manager);
            return modsObj instanceof Iterable<?> mods ? mods : null;
        }
        catch (Throwable t) {
            SimulatedSpawner.LOGGER.debug("无法枚举全局战利品修改器,退回 Forge 链式处理", t);
            return null;
        }
    }

    private static ObjectArrayList<ItemStack> copyStacks(List<ItemStack> source) {
        ObjectArrayList<ItemStack> copy = new ObjectArrayList<>(source.size());
        for (ItemStack stack : source) {
            copy.add(stack.copy());
        }
        return copy;
    }

    /**
     * 规整模拟击杀的掉落:
     * 原版不少生物掉落表把数量写成 0~2(僵尸腐肉、骷髅/凋灵骷髅骨头、箭等),
     * 掷出 0 时物品堆会变成空堆,若该轮其它池的条件都没中,整轮产出就是 0 件,
     * 刷怪笼看起来就像"失效"了。这里把"数量为 0 的真实物品"补成 1 个,
     * 真正的空气/空条目仍然丢弃。
     */
    private static List<ItemStack> normalizeDrops(List<ItemStack> drops) {
        List<ItemStack> result = new java.util.ArrayList<>(drops.size());
        for (ItemStack stack : drops) {
            if (stack.getItem() == Items.AIR) {
                continue;
            }
            if (stack.getCount() <= 0) {
                stack.setCount(1);
            }
            result.add(stack);
        }
        return result;
    }

    /**
     * 模拟钓鱼:每次"生成"改为掷一次钓鱼掉落,容器输送与模拟击杀完全同理。
     *
     * @param luckOfSea 钻石块增益等级:模拟钓鱼的海之眷顾等级
     * @param beacon    信标:额外产出全部宝藏类物品各一份
     * @return true  = 本次"垂钓"成功(鱼获已全部入容器),刷怪笼按 min/max delay 正常继续;
     *         false = 上方没有容器 / 容器空间不足,刷怪笼停止本次运行,重新计时后重试。
     */
    public static boolean simulateFishing(Level level, BlockPos spawnerPos, int luckOfSea, boolean beacon) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        IItemHandler handler = findContainerAbove(serverLevel, spawnerPos);
        if (handler == null) {
            if (SimDebug.enabled()) {
                diagnose("容器@" + spawnerPos,
                    "刷怪笼 {} 上方 {} 格内没有找到带物品栏的容器,模拟已停止。请把箱子/木桶等容器放在刷怪笼正上方。",
                    spawnerPos, SimConfig.CONTAINER_SEARCH_RANGE.get());
            }
            return false;
        }

        if (SimDebug.enabled()) {
            diagnose("模式@" + spawnerPos, "刷怪笼 {} 模拟钓鱼已生效(信标={}, 海之眷顾={})", spawnerPos, beacon, luckOfSea);
        }

        List<ItemStack> loot = rollFishingLoot(serverLevel, spawnerPos, luckOfSea);
        if (beacon) {
            // 动态枚举宝藏池的全部物品(含模组/数据包添加的条目与标签),每种各产出一份
            BeaconRollFlag.setActive(true);
            try {
                Set<Item> treasureItems = collectTreasureItems(serverLevel, spawnerPos);
                for (Item item : treasureItems) {
                    loot.add(new ItemStack(item));
                }
            }
            finally {
                BeaconRollFlag.setActive(false);
            }
        }
        if (loot.isEmpty()) {
            return true;
        }

        if (!tryInsertAll(handler, loot)) {
            if (SimDebug.enabled()) {
                diagnose("容器满@" + spawnerPos,
                    "刷怪笼 {} 上方容器空间不足或拒绝接收物品,模拟已停止。腾出空间后会自动继续。", spawnerPos);
            }
            return false;
        }

        // 视觉反馈(可在通用配置中禁用)
        if (!SimCommonConfig.DISABLE_SPAWNER_PARTICLES.get()) {
            serverLevel.levelEvent(LevelEvent.PARTICLES_MOBBLOCK_SPAWN, spawnerPos, 0);
        }
        return true;
    }

    /**
     * 掷一次钓鱼掉落。
     * <p>
     * 直接掷原版<b>主表</b> {@code minecraft:gameplay/fishing},并把一个新建的浮标作为
     * {@code THIS_ENTITY} 传入 —— 这才是真正的"开阔水域钓鱼":
     * <ul>
     * <li>主表宝藏条目上的 {@code type_specific: fishing_hook / in_open_water: true} 条件
     *     由该浮标满足:原版 {@code FishingHook} 的 {@code openWater} 字段构造时即为 true,
     *     只有实体 tick 时发现水面不"开阔"才会被改成 false,而这里既不加入世界也不 tick;</li>
     * <li>鱼 / 垃圾 / 宝藏的权重不再手写,直接由主表自身的 weight + quality 决定,
     *     海之眷顾作为 luck 传入,与原版浮标逻辑 ({@code withLuck(hook.luck + player.getLuck())}) 完全一致
     *     (原版 quality 为 鱼 -1 / 垃圾 -2 / 宝藏 +2,等价于旧写法的 85-L / 10-2L / 5+2L);</li>
     * <li>数据包或模组对主表与三张子表的改动都会照常生效(旧写法绕过主表,这类改动会被忽略)。</li>
     * </ul>
     */
    private static List<ItemStack> rollFishingLoot(ServerLevel level, BlockPos spawnerPos, int luckOfSea) {
        FishingHook hook = new FishingHook(FakePlayerFactory.getMinecraft(level), level, 0, 0);

        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(spawnerPos))
            .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
            .withParameter(LootContextParams.THIS_ENTITY, hook)
            .withLuck(luckOfSea)
            .create(LootContextParamSets.FISHING);

        LootTable table = level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
        List<ItemStack> drops = table.getRandomItems(params);
        drops.removeIf(ItemStack::isEmpty);
        return drops;
    }

    /** LootTable.pools(生产环境为 SRG 名 f_79109_)。 */
    private static final Field LOOT_TABLE_POOLS = findField(LootTable.class, "pools", "f_79109_");

    /** LootPool.lootEntries(生产环境为 SRG 名 f_79023_)。 */
    private static final Field LOOT_POOL_ENTRIES = findField(LootPool.class, "lootEntries", "f_79023_");

    /** LootTableReference.name(生产环境为 SRG 名 f_79754_)。 */
    private static final Field LOOT_REF_NAME = findField(LootTableReference.class, "name", "f_79754_");

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException e) {
                // try the next name
            }
        }
        return null;
    }

    /**
     * 动态枚举"宝藏类"的全部物品:递归展开 minecraft:gameplay/fishing/treasure 表
     * 的所有池/入口(含物品、物品标签、引用其它表的条目),模组与数据包添加的
     * 宝藏条目同样生效,无需白名单。
     */
    private static Set<Item> collectTreasureItems(ServerLevel level, BlockPos spawnerPos) {
        Set<Item> items = new HashSet<>();
        LootTable treasureTable = level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING_TREASURE);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(spawnerPos))
            .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
            .create(LootContextParamSets.FISHING);
        LootContext context = new LootContext.Builder(params).create(BuiltInLootTables.FISHING_TREASURE);
        collectFromTable(treasureTable, level, context, items, new HashSet<>());
        items.remove(Items.AIR);
        return items;
    }

    private static void collectFromTable(LootTable table, ServerLevel level, LootContext context, Set<Item> out, Set<ResourceLocation> visitedTables) {
        if (LOOT_TABLE_POOLS == null || LOOT_POOL_ENTRIES == null) {
            return;
        }
        try {
            Object poolsObj = LOOT_TABLE_POOLS.get(table);
            // 运行时 LootTable.pools 是 List<LootPool>(兼容数组形式)
            Iterable<?> pools;
            if (poolsObj instanceof Iterable<?> it) {
                pools = it;
            }
            else if (poolsObj instanceof LootPool[] arr) {
                pools = java.util.Arrays.asList(arr);
            }
            else {
                return;
            }
            for (Object poolObj : pools) {
                if (!(poolObj instanceof LootPool pool)) {
                    continue;
                }
                Object entriesObj = LOOT_POOL_ENTRIES.get(pool);
                if (!(entriesObj instanceof LootPoolEntryContainer[] entries)) {
                    continue;
                }
                for (LootPoolEntryContainer entry : entries) {
                    collectFromEntry(entry, level, context, out, visitedTables);
                }
            }
        }
        catch (ReflectiveOperationException e) {
            SimulatedSpawner.LOGGER.warn("枚举钓鱼宝藏物品失败", e);
        }
    }

    private static void collectFromEntry(LootPoolEntryContainer entry, ServerLevel level, LootContext context, Set<Item> out, Set<ResourceLocation> visitedTables) {
        if (entry instanceof LootTableReference reference) {
            // 引用其它表的条目:递归展开
            if (LOOT_REF_NAME == null) {
                return;
            }
            try {
                Object nameObj = LOOT_REF_NAME.get(reference);
                if (nameObj instanceof ResourceLocation name && visitedTables.add(name)) {
                    LootTable table = level.getServer().getLootData().getLootTable(name);
                    collectFromTable(table, level, context, out, visitedTables);
                }
            }
            catch (ReflectiveOperationException e) {
                SimulatedSpawner.LOGGER.warn("枚举钓鱼宝藏物品失败", e);
            }
            return;
        }
        // 物品与标签条目通过 expand 展开,收集每种物品一份
        entry.expand(context, poolEntry -> poolEntry.createItemStack(stack -> {
            if (!stack.isEmpty()) {
                out.add(stack.getItem());
            }
        }, context));
    }

    /**
     * 从刷怪笼正上方开始向上搜索容器,返回第一个带物品栏的方块实体。
     */
    @Nullable
    private static IItemHandler findContainerAbove(ServerLevel level, BlockPos spawnerPos) {
        int range = SimConfig.CONTAINER_SEARCH_RANGE.get();
        for (int i = 1; i <= range; i++) {
            BlockEntity blockEntity = level.getBlockEntity(spawnerPos.above(i));
            if (blockEntity == null) {
                continue;
            }
            IItemHandler handler = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (handler != null && handler.getSlots() > 0) {
                return handler;
            }
        }
        return null;
    }

    /**
     * 先在"幽灵容器"里完整模拟一遍插入,全部成功后才真正写入真实容器。
     * 任一物品放不下则整体放弃(不写入任何物品),刷怪笼停止运行。
     */
    private static boolean tryInsertAll(IItemHandler handler, List<ItemStack> loot) {
        ItemStackHandler phantom = new ItemStackHandler(handler.getSlots());
        for (int i = 0; i < handler.getSlots(); i++) {
            phantom.setStackInSlot(i, handler.getStackInSlot(i).copy());
        }

        for (ItemStack stack : loot) {
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(phantom, stack.copy(), false);
            if (!remainder.isEmpty()) {
                return false;
            }
        }

        for (ItemStack stack : loot) {
            ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
        }
        return true;
    }
}
