package com.hengx.simulatedspawner;

import java.util.Map;

import com.google.gson.JsonObject;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
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
 * 清道夫附魔书配方(无序合成):<b>1 本书 + 8 个肉类</b> → 清道夫 I 附魔书。
 * <p>
 * "肉类"由物品标签 {@code simulatedspawner:meats} 定义(默认含原版各种生/熟肉与鱼类),
 * 整合包可以在数据包里往该标签追加自己的肉类。
 */
public class ScavengerBookRecipe implements CraftingRecipe {

    /** 神化"清道夫"附魔的注册名。 */
    public static final ResourceLocation SCAVENGER_ID = new ResourceLocation("apotheosis", "scavenger");

    /** 肉类标签。 */
    public static final TagKey<Item> MEATS = ItemTags.create(new ResourceLocation(SimulatedSpawner.MODID, "meats"));

    private static final ResourceLocation FALLBACK_ID = new ResourceLocation(SimulatedSpawner.MODID, "scavenger_book");

    private final ResourceLocation id;

    public ScavengerBookRecipe(ResourceLocation id) {
        this.id = id;
    }

    public static Enchantment scavenger() {
        return ForgeRegistries.ENCHANTMENTS.getValue(SCAVENGER_ID);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        if (scavenger() == null) {
            return false;
        }
        int meats = 0;
        int books = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(MEATS)) {
                meats++;
            }
            else if (stack.is(Items.BOOK)) {
                books++;
            }
            else {
                return false;
            }
        }
        return meats == 8 && books == 1;
    }

    /**
     * 生成结果:把清道夫附魔写进 <b>StoredEnchantments</b>。
     * <p>
     * 不能用 {@code ItemStack#enchant}:它写的是 "Enchantments",而附魔书的附魔存在
     * "StoredEnchantments" 里(原版提示栏、铁砧读取的都是后者),写错位置会让合成出来的书无法使用。
     */
    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        Enchantment scavenger = scavenger();
        if (scavenger == null) {
            return ItemStack.EMPTY;
        }
        return EnchantedBooks.create(Map.of(scavenger, 1));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 9;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        Enchantment scavenger = scavenger();
        if (scavenger == null) {
            return new ItemStack(Items.ENCHANTED_BOOK);
        }
        return EnchantedBooks.create(Map.of(scavenger, 1));
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (int i = 0; i < 8; i++) {
            ingredients.add(Ingredient.of(MEATS));
        }
        ingredients.add(Ingredient.of(Items.BOOK));
        return ingredients;
    }

    @Override
    public ResourceLocation getId() {
        return this.id != null ? this.id : FALLBACK_ID;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SimulatedSpawner.SCAVENGER_SERIALIZER.get();
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    public static class Serializer implements RecipeSerializer<ScavengerBookRecipe> {

        @Override
        public ScavengerBookRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ScavengerBookRecipe(id);
        }

        @Override
        public ScavengerBookRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new ScavengerBookRecipe(id);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ScavengerBookRecipe recipe) {
            // 无参数需要同步
        }
    }
}
