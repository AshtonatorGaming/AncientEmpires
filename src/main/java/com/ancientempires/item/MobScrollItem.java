package com.ancientempires.item;

import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.structure.AeRemap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A captured mob for a spawner's slots: any entity that is not one of our NPCs
 * (those go in their own scrolls), by id and the data to give it. Made by the
 * capture wand and by structures (AW spawners naming vanilla or modded mobs).
 * The id resolves when it spawns, through {@link AeRemap}, so a mod installed
 * later just works; an id nothing knows becomes {@code structures.missingEntity}
 * (or nothing, if that is empty).
 */
public class MobScrollItem extends Item
{
    public static final String ID_KEY = "MobId";
    public static final String DATA_KEY = "MobData";

    public MobScrollItem(Properties properties)
    {
        super(properties);
    }

    /** A scroll for {@code id}, with {@code data} applied on spawn (may be empty). */
    public static ItemStack of(Item item, String id, CompoundTag data, int count)
    {
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        stack.getOrCreateTag().putString(ID_KEY, id);
        if (!data.isEmpty())
        {
            stack.getOrCreateTag().put(DATA_KEY, data);
        }
        return stack;
    }

    /** A capture of a living entity: its id and saved data, less what ties it to its old place. */
    public static ItemStack capture(Item item, Entity entity)
    {
        CompoundTag data = new CompoundTag();
        entity.saveWithoutId(data);
        for (String key : new String[] {"Pos", "Motion", "Rotation", "UUID", "FallDistance", "Fire", "Air", "OnGround", "PortalCooldown",
                "Leash", "Brain", "HurtTime", "HurtByTimestamp", "DeathTime", "Passengers"})
        {
            data.remove(key);
        }
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        ItemStack stack = of(item, id == null ? "" : id.toString(), data, 1);
        stack.setHoverName(Component.translatable("item.ancientempires.mob_scroll.named", entity.getName()));
        return stack;
    }

    public static String mobId(ItemStack stack)
    {
        return stack.hasTag() ? stack.getTag().getString(ID_KEY) : "";
    }

    /** The entity type this scroll spawns here, after renames and the fallback; empty for none. */
    public static Optional<EntityType<?>> resolvedType(ItemStack stack)
    {
        Optional<EntityType<?>> type = EntityType.byString(AeRemap.entityId(mobId(stack)));
        if (type.isPresent())
        {
            return type;
        }
        String fallback = AncientEmpiresConfig.structuresMissingEntity;
        return fallback.isEmpty() ? Optional.empty() : EntityType.byString(fallback);
    }

    /**
     * Spawns one at {@code pos}. With data, the entity is made from it as
     * captured (gear, name, variant); without, it spawns fresh as a spawner
     * would, picking its own gear. Null when nothing could be made.
     */
    @Nullable
    public static Entity spawn(ServerLevel level, ItemStack stack, BlockPos pos)
    {
        Optional<EntityType<?>> type = resolvedType(stack);
        if (type.isEmpty())
        {
            return null;
        }
        CompoundTag data = stack.hasTag() ? stack.getTag().getCompound(DATA_KEY) : new CompoundTag();
        boolean fallback = !type.get().equals(EntityType.byString(AeRemap.entityId(mobId(stack))).orElse(null));
        if (data.isEmpty() || fallback)
        {
            return type.get().spawn(level, pos, MobSpawnType.SPAWNER);
        }
        CompoundTag tag = data.copy();
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type.get());
        tag.putString("id", id == null ? "" : id.toString());
        Optional<Entity> made = EntityType.create(tag, level);
        if (made.isEmpty())
        {
            return null;
        }
        Entity entity = made.get();
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
        if (entity instanceof Mob mob)
        {
            mob.yHeadRot = mob.getYRot();
        }
        level.addFreshEntityWithPassengers(entity);
        return entity;
    }

    @Override
    public Component getName(ItemStack stack)
    {
        String id = mobId(stack);
        Optional<EntityType<?>> type = EntityType.byString(AeRemap.entityId(id));
        return type.isPresent()
                ? Component.translatable("item.ancientempires.mob_scroll.named", type.get().getDescription())
                : Component.translatable("item.ancientempires.mob_scroll.named", id.isEmpty() ? "?" : id);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        String id = mobId(stack);
        tooltip.add(Component.literal(id).withStyle(ChatFormatting.DARK_GRAY));
        if (EntityType.byString(AeRemap.entityId(id)).isEmpty())
        {
            tooltip.add(Component.translatable("item.ancientempires.mob_scroll.missing").withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("item.ancientempires.mob_scroll.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
