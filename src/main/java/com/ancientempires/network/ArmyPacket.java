package com.ancientempires.network;

import com.ancientempires.client.CommandScreen;
import com.ancientempires.entity.npc.NpcArmy;
import com.ancientempires.entity.npc.NpcStance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server to client: the player's army for the command screen; {@code open} opens it, else it refreshes an open one. */
public class ArmyPacket
{
    /**
     * One soldier as the screen shows it. {@code name} and {@code rankName} are
     * text components as JSON; {@code order} and {@code formation} are ordinals
     * ({@code NpcOrder}, {@code NpcFormation}), {@code ownOrder} whether the
     * unit holds it itself (top of a chain, or detached). {@code stance} packs
     * its stance: engage ordinal, fire ordinal shifted by 2, shields up at bit 4;
     * {@code width} its dragged frontage (0 for the configured one), {@code morale} 0 to 100.
     */
    public record Entry(UUID id, String name, int rank, int level, float health, int order, boolean ownOrder, int formation,
            @Nullable UUID leader, int members, float distance, String unitName, boolean archer, boolean mounted, String rankName, int unitNumber,
            float spread, int width, int stance, float morale)
    {
        /** Packs a stance for {@code stance}. */
        public static int pack(NpcStance stance)
        {
            return stance.engage().ordinal() | stance.fire().ordinal() << 2 | (stance.shieldsUp() ? 16 : 0);
        }

        public NpcStance unpackStance()
        {
            return new NpcStance(NpcStance.Engage.values()[Math.min(this.stance & 3, NpcStance.Engage.values().length - 1)],
                    NpcStance.Fire.values()[Math.min(this.stance >> 2 & 3, NpcStance.Fire.values().length - 1)], (this.stance & 16) != 0);
        }
    }

    private final boolean open;
    private final List<Entry> entries;

    public ArmyPacket(boolean open, List<Entry> entries)
    {
        this.open = open;
        this.entries = entries;
    }

    public static void encode(ArmyPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBoolean(packet.open);
        buffer.writeVarInt(packet.entries.size());
        for (Entry entry : packet.entries)
        {
            buffer.writeUUID(entry.id());
            buffer.writeUtf(entry.name(), 1024);
            buffer.writeByte(entry.rank());
            buffer.writeVarInt(entry.level());
            buffer.writeFloat(entry.health());
            buffer.writeByte(entry.order());
            buffer.writeBoolean(entry.ownOrder());
            buffer.writeByte(entry.formation());
            buffer.writeBoolean(entry.leader() != null);
            if (entry.leader() != null)
            {
                buffer.writeUUID(entry.leader());
            }
            buffer.writeVarInt(entry.members());
            buffer.writeFloat(entry.distance());
            buffer.writeUtf(entry.unitName(), NpcArmy.NAME_LENGTH);
            buffer.writeBoolean(entry.archer());
            buffer.writeBoolean(entry.mounted());
            buffer.writeUtf(entry.rankName(), 256);
            buffer.writeByte(entry.unitNumber());
            buffer.writeFloat(entry.spread());
            buffer.writeByte(entry.width());
            buffer.writeByte(entry.stance());
            buffer.writeFloat(entry.morale());
        }
    }

    public static ArmyPacket decode(FriendlyByteBuf buffer)
    {
        boolean open = buffer.readBoolean();
        int count = Math.min(buffer.readVarInt(), NpcArmy.MAX);
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
        {
            UUID id = buffer.readUUID();
            String name = buffer.readUtf(1024);
            int rank = buffer.readByte();
            int level = buffer.readVarInt();
            float health = buffer.readFloat();
            int order = buffer.readByte();
            boolean ownOrder = buffer.readBoolean();
            int formation = buffer.readByte();
            UUID leader = buffer.readBoolean() ? buffer.readUUID() : null;
            int members = buffer.readVarInt();
            float distance = buffer.readFloat();
            String unitName = buffer.readUtf(NpcArmy.NAME_LENGTH);
            boolean archer = buffer.readBoolean();
            boolean mounted = buffer.readBoolean();
            String rankName = buffer.readUtf(256);
            int unitNumber = buffer.readByte();
            float spread = buffer.readFloat();
            int width = buffer.readByte();
            int stance = buffer.readByte();
            float morale = buffer.readFloat();
            entries.add(new Entry(id, name, rank, level, health, order, ownOrder, formation, leader, members, distance, unitName, archer, mounted, rankName, unitNumber,
                    spread, width, stance, morale));
        }
        return new ArmyPacket(open, entries);
    }

    public static void handle(ArmyPacket packet, Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CommandScreen.show(packet.open, packet.entries));
    }
}
