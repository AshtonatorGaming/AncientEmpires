package com.ancientempires.gate;

import com.ancientempires.AncientEmpires;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * AW's seven gates. How each moves: a vertical gate's wall rises into the
 * frame above, a single gate's slides aside, a double gate's two halves part,
 * and a drawbridge falls outward to lie flat as a bridge. Iron gates draw bars,
 * wooden ones a solid wall (AW's model types). {@code awName} is how AW's
 * templates name them.
 */
public enum GateType
{
    VERTICAL_WOOD("gate.verticalWooden", "gate_vertical_wooden", Motion.VERTICAL, false),
    VERTICAL_IRON("gate.verticalIron", "gate_vertical_iron", Motion.VERTICAL, true),
    SINGLE_WOOD("gate.singleWood", "gate_single_wood", Motion.SLIDE, false),
    SINGLE_IRON("gate.singleIron", "gate_single_iron", Motion.SLIDE, true),
    DOUBLE_WOOD("gate.doubleWood", "gate_double_wood", Motion.SPLIT, false),
    DOUBLE_IRON("gate.doubleIron", "gate_double_iron", Motion.SPLIT, true),
    DRAWBRIDGE("gate.drawbridge", "gate_drawbridge", Motion.BRIDGE, false);

    public enum Motion
    {
        VERTICAL,
        SLIDE,
        SPLIT,
        BRIDGE
    }

    public final String awName;
    public final String itemId;
    public final Motion motion;
    public final boolean iron;

    GateType(String awName, String itemId, Motion motion, boolean iron)
    {
        this.awName = awName;
        this.itemId = itemId;
        this.motion = motion;
        this.iron = iron;
    }

    /** AW's model sheet: the bridge's own, else wood or iron. */
    public ResourceLocation texture()
    {
        String sheet = this == DRAWBRIDGE ? "gate_bridge_wood_1" : this.iron ? "gate_iron_1" : "gate_wood_1";
        return ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, "textures/entity/gate/" + sheet + ".png");
    }

    /** Iron gates draw AW's bars; wooden ones its solid wall. */
    public boolean bars()
    {
        return this.iron;
    }

    /** AW's own gate sounds (shipped under this mod's name), played once as the gate starts to move. */
    public SoundEvent moveSound()
    {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, this.iron ? "iron_gate" : "wooden_gate"));
    }

    public SoundEvent hurtSound()
    {
        return this.iron ? SoundEvents.ZOMBIE_ATTACK_IRON_DOOR : SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR;
    }

    public SoundEvent breakSound()
    {
        return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID, this.iron ? "iron_gate_break" : "wooden_gate_break"));
    }

    public static GateType byAwName(String name)
    {
        for (GateType type : values())
        {
            if (type.awName.equals(name))
            {
                return type;
            }
        }
        return VERTICAL_WOOD;
    }

    public static GateType byOrdinal(int ordinal)
    {
        GateType[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }
}
