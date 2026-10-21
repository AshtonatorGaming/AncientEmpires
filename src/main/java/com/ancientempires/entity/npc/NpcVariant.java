package com.ancientempires.entity.npc;

import com.ancientempires.AncientEmpires;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Starting skin, body scale, and weapon for one spawn egg.
 * The skin id is the old Ancient Warfare name, such as {@code viking.soldier}.
 * Rendering maps that name onto a bundled texture. A raw resource location
 * still passes through {@link NpcEntity#parseSkinId(String)} unchanged.
 * Gear is resolved when the egg spawns, so a missing Spartan Weaponry install
 * falls back without a compile dependency.
 */
public enum NpcVariant
{
    VIKING_SOLDIER("viking.soldier", "viking_soldier", 1.0F, Kit.SPEAR_SHIELD),
    EMPIRE_SOLDIER("empire.soldier", "empire_soldier", 1.0F, Kit.LONGSWORD),
    BRIGAND("brigand.soldier", "brigand_soldier", 1.0F, Kit.SPEAR),
    ARCHER("empire.archer", "empire_archer", 1.0F, Kit.LONGBOW),
    DWARF_SOLDIER("dwarf.soldier", "dwarf_soldier", 0.75F, Kit.HALBERD),
    GIANT("giant.soldier", "giant_soldier", 2.0F, Kit.GREATSWORD),
    CIVILIAN("civilian.worker", "civilian_worker", 1.0F, Kit.NONE),
    LEADER("empire.leader", "empire_leader", 1.0F, Kit.DIAMOND_LONGSWORD);

    private static final String SPARTAN = "spartanweaponry";
    private static final Map<String, NpcVariant> BY_SKIN = new HashMap<>();

    static
    {
        for (NpcVariant variant : values())
        {
            BY_SKIN.put(variant.skinId, variant);
        }
    }

    private final String skinId;
    private final ResourceLocation texture;
    private final float bodyScale;
    private final Kit kit;

    NpcVariant(String skinId, String textureFile, float bodyScale, Kit kit)
    {
        this.skinId = skinId;
        this.texture = ResourceLocation.parse(AncientEmpires.MOD_ID + ":textures/entity/npc/" + textureFile + ".png");
        this.bodyScale = bodyScale;
        this.kit = kit;
    }

    public String skinId()
    {
        return this.skinId;
    }

    public ResourceLocation texture()
    {
        return this.texture;
    }

    public float bodyScale()
    {
        return this.bodyScale;
    }

    @Nullable
    public static NpcVariant bySkinId(String skinId)
    {
        if (skinId == null || skinId.isEmpty())
        {
            return null;
        }
        return BY_SKIN.get(skinId);
    }

    /**
     * Skin, scale, and empty-hand gear. Existing items in either hand are left alone.
     */
    public void apply(NpcEntity npc)
    {
        npc.assignSkin(this.skinId);
        npc.setBodyScale(this.bodyScale);
        ItemStack main = this.kit.mainHand();
        if (!main.isEmpty() && npc.getMainHandItem().isEmpty())
        {
            npc.setItemSlot(EquipmentSlot.MAINHAND, main);
        }
        ItemStack off = this.kit.offHand();
        if (!off.isEmpty() && npc.getOffhandItem().isEmpty())
        {
            npc.setItemSlot(EquipmentSlot.OFFHAND, off);
        }
    }

    /**
     * Spartan Weaponry marks longswords, halberds, and greatswords as two-handed,
     * so those kits leave the off hand empty. The spear is one-handed.
     */
    private enum Kit
    {
        NONE,
        SPEAR,
        SPEAR_SHIELD,
        LONGSWORD,
        HALBERD,
        DIAMOND_LONGSWORD,
        GREATSWORD,
        LONGBOW;

        private ItemStack mainHand()
        {
            if (this == SPEAR || this == SPEAR_SHIELD)
            {
                return spartan("iron_spear", Items.IRON_SWORD);
            }
            if (this == LONGSWORD)
            {
                return spartan("iron_longsword", Items.IRON_SWORD);
            }
            if (this == HALBERD)
            {
                return spartan("iron_halberd", Items.IRON_SWORD);
            }
            if (this == DIAMOND_LONGSWORD)
            {
                return spartan("diamond_longsword", Items.IRON_SWORD);
            }
            if (this == GREATSWORD)
            {
                return spartan("iron_greatsword", Items.IRON_SWORD);
            }
            if (this == LONGBOW)
            {
                return spartan("iron_longbow", Items.BOW);
            }
            return ItemStack.EMPTY;
        }

        private ItemStack offHand()
        {
            if (this == SPEAR_SHIELD)
            {
                return new ItemStack(Items.SHIELD);
            }
            return ItemStack.EMPTY;
        }

        private static ItemStack spartan(String path, Item fallback)
        {
            if (ModList.get().isLoaded(SPARTAN))
            {
                ResourceLocation id = ResourceLocation.parse(SPARTAN + ":" + path);
                if (ForgeRegistries.ITEMS.containsKey(id))
                {
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    if (item != null && item != Items.AIR)
                    {
                        return new ItemStack(item);
                    }
                }
            }
            return new ItemStack(fallback);
        }
    }
}
