package com.ancientempires.structure;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.item.MobScrollItem;
import com.ancientempires.item.NpcSpawnEggItem;
import com.ancientempires.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * AW's Advanced Loot Chest, Basket and Urn, carried into structures. The
 * chest and basket stand-ins are vanilla containers given the converted loot
 * table, so vanilla fills them on first open; urns are decorated pots, which
 * hold nothing, so an urn's loot spills out when it is first used. What vanilla
 * cannot do is kept in the block entity's persistent data ({@code AeLoot}) and
 * done here on first use: extra rolls, the trapped chest's mob (an AW NPC
 * through its roster scroll), its potion (splash: everyone within 4 blocks),
 * and its message. Then the data is gone and it is an ordinary container.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class AwLoot
{
    public static final String KEY = "AeLoot";
    private static final String NPC_PREFIX = "ancientwarfarenpc:faction.";

    private AwLoot()
    {
    }

    /** Fills in a placed stand-in from AW's rule: its table, any items AW saved in it, and the extras. */
    public static void place(ServerLevelAccessor level, BlockPos pos, CompoundTag rule)
    {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null)
        {
            return;
        }
        CompoundTag teData = rule.getCompound("teData");
        CompoundTag settings = teData.getCompound("lootSettings");
        String table = settings.getBoolean("hasLoot") ? AwsLootConverter.tableId(settings.getString("lootTableName")) : "";
        if (blockEntity instanceof RandomizableContainerBlockEntity container)
        {
            CompoundTag load = new CompoundTag();
            load.put("Items", teData.getList("Items", Tag.TAG_COMPOUND));
            container.load(load);
            if (!table.isEmpty())
            {
                container.setLootTable(ResourceLocation.tryParse(table), level.getRandom().nextLong());
            }
        }
        CompoundTag extras = new CompoundTag();
        int rolls = Math.max(1, settings.getInt("lootRolls"));
        boolean container = blockEntity instanceof Container;
        if (!table.isEmpty() && (rolls > 1 || !container))
        {
            // A pot rolls all of its loot here; a container lets vanilla roll the first.
            extras.putString("table", table);
            extras.putInt("rolls", container ? rolls - 1 : rolls);
        }
        if (settings.getBoolean("spawnEntity") && !settings.getString("entity").isEmpty())
        {
            extras.putString("entity", settings.getString("entity"));
            extras.put("entityNBT", settings.getCompound("entityNBT"));
        }
        if (!settings.getList("effects", Tag.TAG_COMPOUND).isEmpty())
        {
            extras.put("effects", settings.getList("effects", Tag.TAG_COMPOUND));
            extras.putBoolean("splash", settings.getBoolean("splashPotion"));
        }
        if (settings.getBoolean("hasMessage") && !settings.getString("playerMessage").isEmpty())
        {
            extras.putString("message", settings.getString("playerMessage"));
        }
        if (!extras.isEmpty())
        {
            blockEntity.getPersistentData().put(KEY, extras);
            blockEntity.setChanged();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getEntity().isSpectator())
        {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if (blockEntity == null || !blockEntity.getPersistentData().contains(KEY))
        {
            return;
        }
        CompoundTag extras = blockEntity.getPersistentData().getCompound(KEY);
        blockEntity.getPersistentData().remove(KEY);
        blockEntity.setChanged();
        open(level, event.getPos(), blockEntity, extras, event.getEntity());
    }

    private static void open(ServerLevel level, BlockPos pos, BlockEntity blockEntity, CompoundTag extras, Player player)
    {
        if (blockEntity instanceof RandomizableContainerBlockEntity container)
        {
            // Vanilla's own roll first, so the extra ones add to it.
            container.unpackLootTable(player);
        }
        if (extras.contains("table"))
        {
            LootTable table = level.getServer().getLootData().getLootTable(ResourceLocation.tryParse(extras.getString("table")));
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withLuck(player.getLuck())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .create(LootContextParamSets.CHEST);
            Container target = blockEntity instanceof Container container ? container : new SimpleContainer(27);
            for (int roll = 0; roll < extras.getInt("rolls"); roll++)
            {
                table.fill(target, params, level.getRandom().nextLong());
            }
            if (!(blockEntity instanceof Container))
            {
                Containers.dropContents(level, pos.above(), target);
            }
        }
        if (extras.contains("entity"))
        {
            spawnGuard(level, pos, extras.getString("entity"), extras.getCompound("entityNBT"));
        }
        if (extras.contains("effects"))
        {
            boolean splash = extras.getBoolean("splash");
            for (Player target : splash ? level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(4.0D)) : List.<Player>of(player))
            {
                for (Tag entry : extras.getList("effects", Tag.TAG_COMPOUND))
                {
                    MobEffectInstance effect = effect((CompoundTag) entry);
                    if (effect != null)
                    {
                        target.addEffect(effect);
                    }
                }
            }
        }
        if (extras.contains("message"))
        {
            player.sendSystemMessage(Component.literal(extras.getString("message")));
        }
    }

    /** The chest's trap: an AW NPC by its roster scroll, anything else as a captured mob, just above. */
    private static void spawnGuard(ServerLevel level, BlockPos pos, String id, CompoundTag data)
    {
        BlockPos at = level.isEmptyBlock(pos.above()) && level.isEmptyBlock(pos.above(2)) ? pos.above() : pos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom()));
        if (id.startsWith(NPC_PREFIX))
        {
            Item egg = ModItems.eggFor(data.getString("factionName") + "." + id.substring(NPC_PREFIX.length()));
            if (egg != null)
            {
                NpcEntity npc = NpcSpawnEggItem.spawnFrom(level, new ItemStack(egg), at, null);
                if (npc != null)
                {
                    npc.setPersistenceRequired();
                }
            }
            return;
        }
        CompoundTag mob = data.copy();
        mob.remove("ForgeCaps");
        mob.remove("ForgeData");
        if (MobScrollItem.spawn(level, MobScrollItem.of(ModItems.MOB_SCROLL.get(), id, mob, 1), at) instanceof Mob spawned)
        {
            spawned.setPersistenceRequired();
        }
    }

    /** A potion effect as AW saved it (Forge 1.12: {@code RegistryName}, {@code Duration}, {@code Amplifier}). */
    private static MobEffectInstance effect(CompoundTag tag)
    {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("RegistryName"));
        MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
        if (effect == null)
        {
            return null;
        }
        return new MobEffectInstance(effect, Math.max(1, tag.getInt("Duration")), tag.getByte("Amplifier"),
                tag.getBoolean("Ambient"), !tag.contains("ShowParticles") || tag.getBoolean("ShowParticles"));
    }
}
