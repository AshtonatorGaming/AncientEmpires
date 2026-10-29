package com.ancientempires.entity.npc;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/**
 * How a unit fights under whatever order it holds (docs/army-command-design.md):
 * when it engages, when its archers loose, and whether its shields stay up.
 * Set on a leader and taken by everyone under it that has none of its own.
 *
 * @param engage   how readily it fights
 * @param fire     when its archers shoot
 * @param shieldsUp whether formed soldiers keep their shields raised while they stand
 */
public record NpcStance(Engage engage, Fire fire, boolean shieldsUp)
{
    public static final NpcStance DEFAULT = new NpcStance(Engage.HOLD_LINE, Fire.AT_WILL, false);

    public enum Engage
    {
        /** Reaches {@code chain.aggressiveLeash} past the order's leash, and the leader looks that much further for foes. */
        AGGRESSIVE,
        /** Fights within the order's leash and goes back to its slots after (the default). */
        HOLD_LINE,
        /** Fights only what attacks it, its unit, or comes on top of it. */
        PASSIVE
    }

    public enum Fire
    {
        /** Each archer shoots when it has a shot. */
        AT_WILL,
        /** Archers draw on nothing unless cornered. */
        HOLD,
        /** Archers hold the full draw and loose together, every {@code chain.volleyTicks}. */
        VOLLEY
    }

    public NpcStance with(Engage engage)
    {
        return new NpcStance(engage, this.fire, this.shieldsUp);
    }

    public NpcStance with(Fire fire)
    {
        return new NpcStance(this.engage, fire, this.shieldsUp);
    }

    public NpcStance withShields(boolean shieldsUp)
    {
        return new NpcStance(this.engage, this.fire, shieldsUp);
    }

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("Engage", this.engage.name().toLowerCase(Locale.ROOT));
        tag.putString("Fire", this.fire.name().toLowerCase(Locale.ROOT));
        tag.putBoolean("Shields", this.shieldsUp);
        return tag;
    }

    public static NpcStance load(CompoundTag tag)
    {
        return new NpcStance(byName(Engage.class, tag.getString("Engage"), Engage.HOLD_LINE), byName(Fire.class, tag.getString("Fire"), Fire.AT_WILL),
                tag.getBoolean("Shields"));
    }

    private static <E extends Enum<E>> E byName(Class<E> type, String name, E fallback)
    {
        for (E value : type.getEnumConstants())
        {
            if (value.name().toLowerCase(Locale.ROOT).equals(name))
            {
                return value;
            }
        }
        return fallback;
    }
}
