package com.ancientempires.item;

import com.ancientempires.entity.npc.NpcEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraftforge.common.ForgeSpawnEggItem;

import java.util.function.Supplier;

/**
 * Spawn egg that records the player as owner while the entity is created.
 * The claim is read from the spawn event before the tracking packet.
 */
public class NpcSpawnEggItem extends ForgeSpawnEggItem
{
    public NpcSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Item.Properties properties)
    {
        super(type, backgroundColor, highlightColor, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        boolean claim = player != null && !context.getLevel().isClientSide;
        if (claim)
        {
            NpcEntity.beginSpawnClaim(player.getUUID());
        }
        try
        {
            return super.useOn(context);
        }
        finally
        {
            if (claim)
            {
                NpcEntity.endSpawnClaim();
            }
        }
    }
}
