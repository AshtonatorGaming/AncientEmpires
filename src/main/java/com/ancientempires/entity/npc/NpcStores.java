package com.ancientempires.entity.npc;

import com.ancientempires.block.TownHallBlockEntity;
import com.ancientempires.block.WorksiteBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.structure.StructureClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The places an order names (a courier's stops, an upkeep pantry, a listed
 * worksite), read safely. A stored position is read only in its own dimension
 * and in a loaded chunk: {@code Level.getBlockEntity} on the server loads (or
 * generates) the chunk on the main thread, and a check every two seconds per
 * NPC turned that into a steady stutter. A container is used only when it is
 * not another player's worksite and not inside a faction structure still held
 * (its loot locked until claimed, as for players).
 */
public final class NpcStores
{
    private NpcStores()
    {
    }

    /** Whether a stored spot can be read now: this dimension, and a loaded chunk. */
    public static boolean readable(Level level, String dimension, BlockPos pos)
    {
        return dimension.equals(level.dimension().location().toString()) && level.isLoaded(pos);
    }

    /** The block entity at a stored spot, or null when it is elsewhere or not loaded (never loads a chunk). */
    @Nullable
    public static BlockEntity at(Level level, String dimension, BlockPos pos)
    {
        return readable(level, dimension, pos) ? level.getBlockEntity(pos) : null;
    }

    /** The item handler at {@code pos} that {@code npc} may move items in and out of, or null. */
    @Nullable
    public static IItemHandler handler(NpcEntity npc, BlockPos pos)
    {
        Level level = npc.level();
        if (!level.isLoaded(pos))
        {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null || !mayUse(npc, entity))
        {
            return null;
        }
        return entity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
    }

    /**
     * The backpack slots (0 to 8) of food a worker keeps when it banks or
     * deposits everything: whole stacks until it carries {@code upkeep.carryRations},
     * so a courier or trader is not left to walk to the hall at every meal.
     */
    static boolean[] keptFood(NpcInventory inventory)
    {
        boolean[] kept = new boolean[NpcInventory.BACKPACK_SLOTS];
        double carried = 0.0D;
        for (int index = 0; index < NpcInventory.BACKPACK_SLOTS && carried < AncientEmpiresConfig.upkeepCarryRations; index++)
        {
            ItemStack stack = inventory.getItem(NpcInventory.BACKPACK_START + index);
            double each = stack.isEdible() ? TownHallBlockEntity.rations(stack) : 0.0D;
            if (each > 0.0D)
            {
                kept[index] = true;
                carried += each * stack.getCount();
            }
        }
        return kept;
    }

    /** Not another player's worksite, and not in a faction structure still held. */
    public static boolean mayUse(NpcEntity npc, BlockEntity entity)
    {
        if (entity instanceof WorksiteBlockEntity site && site.owner() != null && !site.owner().equals(npc.getOwner()))
        {
            return false;
        }
        return !(npc.level() instanceof ServerLevel level) || StructureClaims.held(level, entity.getBlockPos()) == null;
    }
}
