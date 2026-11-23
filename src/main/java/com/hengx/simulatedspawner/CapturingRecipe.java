package com.hengx.simulatedspawner;

import java.util.Map;

import com.google.gson.JsonObject;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 捕捉附魔书配方(无序合成,挂在原版工作台配方类型上):
 * <ul>
 * <li>{@code upgrade = false}:1 本书 + 4 个鸡蛋 → 捕捉 I 附魔书;</li>
 * <li>{@code upgrade = true}:1 本带捕捉的附魔书 + 4 个鸡蛋 → 捕捉等级 +1(上限为该附魔自身最高等级,神化默认为 3)。</li>
 * </ul>
 * 升级时会保留附魔书上原有的其它附魔。
 */
public class CapturingRecipe implements CraftingRecipe {

    /** 神化"捕捉"附魔的注册名。 */
    public static final ResourceLocation CAPTURING_ID = new ResourceLocation("apotheosis", "capturing");

    /** 升级上限:最高 3 级;若该附魔自身最高等级更低,则取更低者。 */
    public static final int MAX_LEVEL = 3;

    private static final ResourceLocation FALLBACK_ID = new ResourceLocation(SimulatedSpawner.MODID, "capturing");

    private final ResourceLocation id;

    private final boolean upgrade;

    public CapturingRecipe(ResourceLocation id, boolean upgrade) {
        this.id = id;
        this.upgrade = upgrade;
    }

    public static Enchantment capturing() {
        return ForgeRegistries.ENCHANTMENTS.getValue(CAPTURING_ID);
    }

    private static int levelOf(ItemStack stack, Enchantment capturing) {
        return stack.is(Items.ENCHANTED_BOOK) ? EnchantedBooks.level(stack, capturing) : 0;
    }

    /** 实际升级上限(用户要求 3 级封顶)。 */
    private static int maxLevel(Enchantment capturing) {
        return Math.min(MAX_LEVEL, capturing.getMaxLevel());
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        Enchantment capturing = capturing();
        if (capturing == null) {
            return false;
        }

        int eggs = 0;
        ItemStack book = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(Items.EGG)) {
                eggs++;
                continue;
            }
            if (book.isEmpty() && isBookCandidate(stack, capturing)) {
                book = stack;
                continue;
            }
            // 出现任何其它物品都不合成
            return false;
        }

        if (eggs != 4 || book.isEmpty()) {
            return false;
        }
        if (this.upgrade) {
            int current = levelOf(book, capturing);
            return current >= 1 && current < maxLevel(capturing);
        }
        return book.is(Items.BOOK);
    }

    private boolean isBookCandidate(ItemStack stack, Enchantment capturing) {
        if (this.upgrade) {
            return stack.is(Items.ENCHANTED_BOOK) && levelOf(stack, capturing) >= 1;
        }
        return stack.is(Items.BOOK);
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        Enchantment capturing = capturing();
        if (capturing == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isBookCandidate(stack, capturing)) {
                return createResult(stack, capturing);
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 生成结果:保留原书上的其它附魔,并把捕捉等级 +1(或设为 1)。
     * <p>
     * 必须用 {@link EnchantedBooks#create} 把附魔写进 <b>StoredEnchantments</b>:
     * {@code ItemStack#enchant} 会写到 "Enchantments" 上,那样造出来的书在原版读取路径
     * (提示栏、铁砧)上都没有附魔 —— 就是"做出来的书无法使用"。
     */
    private ItemStack createResult(ItemStack book, Enchantment capturing) {
        Map<Enchantment, Integer> enchantments = new java.util.LinkedHashMap<>();
        if (book.is(Items.ENCHANTED_BOOK)) {
            enchantments.putAll(EnchantedBooks.read(book));
        }
        int newLevel = this.upgrade ? levelOf(book, capturing) + 1 : 1;
        enchantments.put(capturing, Math.min(newLevel, maxLevel(capturing)));
        return EnchantedBooks.create(enchantments);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 5;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        Enchantment capturing = capturing();
        if (capturing == null) {
            return new ItemStack(Items.ENCHANTED_BOOK);
        }
        return EnchantedBooks.create(Map.of(capturing, this.upgrade ? 2 : 1));
    }

    /** 仅用于配方查看器(JEI/配方书)展示成分。 */
    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (int i = 0; i < 4; i++) {
            ingredients.add(Ingredient.of(Items.EGG));
        }
        ingredients.add(Ingredient.of(this.upgrade ? Items.ENCHANTED_BOOK : Items.BOOK));
        return ingredients;
    }

    @Override
    public ResourceLocation getId() {
        return this.id != null ? this.id : FALLBACK_ID;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SimulatedSpawner.CAPTURING_SERIALIZER.get();
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    public static class Serializer implements RecipeSerializer<CapturingRecipe> {

        @Override
        public CapturingRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new CapturingRecipe(id, GsonHelper.getAsBoolean(json, "upgrade", false));
        }

        @Override
        public CapturingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new CapturingRecipe(id, buf.readBoolean());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, CapturingRecipe recipe) {
            buf.writeBoolean(recipe.upgrade);
        }
    }
}
