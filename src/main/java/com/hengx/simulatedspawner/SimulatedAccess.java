package com.hengx.simulatedspawner;

/**
 * 由 Mixin 注入到 {@code ApothSpawnerTile} 上的接口,
 * 提供"模拟掉落"/"模拟钓鱼"模式标记、钻石块增益等级、信标标记与启示之证标记的读写
 * (全部随方块实体一起持久化)。
 */
public interface SimulatedAccess {

    boolean simulatedspawner$isSimulated();

    void simulatedspawner$setSimulated(boolean simulated);

    boolean simulatedspawner$isFishing();

    void simulatedspawner$setFishing(boolean fishing);

    int simulatedspawner$getBonusLevel();

    void simulatedspawner$setBonusLevel(int level);

    boolean simulatedspawner$isBeacon();

    void simulatedspawner$setBeacon(boolean beacon);

    boolean simulatedspawner$isRevelation();

    void simulatedspawner$setRevelation(boolean revelation);
}
