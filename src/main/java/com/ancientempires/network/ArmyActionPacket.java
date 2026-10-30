package com.ancientempires.network;

import com.ancientempires.entity.npc.NpcArmy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client to server: the command screen asks for the army ({@code subject}
 * null: just the list) or for an action on one soldier ({@link NpcArmy#act});
 * either way the list comes back.
 */
public class ArmyActionPacket
{
    private final boolean open;
    @Nullable
    private final NpcArmy.Action action;
    @Nullable
    private final UUID subject;
    @Nullable
    private final UUID other;
    private final String text;

    public ArmyActionPacket(boolean open, @Nullable NpcArmy.Action action, @Nullable UUID subject, @Nullable UUID other, String text)
    {
        this.open = open;
        this.action = action;
        this.subject = subject;
        this.other = other;
        this.text = text;
    }

    /** Asks for the army to open the screen with. */
    public static ArmyActionPacket request()
    {
        return new ArmyActionPacket(true, null, null, null, "");
    }

    public static void encode(ArmyActionPacket packet, FriendlyByteBuf buffer)
    {
        buffer.writeBoolean(packet.open);
        buffer.writeBoolean(packet.action != null && packet.subject != null);
        if (packet.action != null && packet.subject != null)
        {
            buffer.writeEnum(packet.action);
            buffer.writeUUID(packet.subject);
            buffer.writeBoolean(packet.other != null);
            if (packet.other != null)
            {
                buffer.writeUUID(packet.other);
            }
            buffer.writeUtf(packet.text, NpcArmy.NAME_LENGTH);
        }
    }

    public static ArmyActionPacket decode(FriendlyByteBuf buffer)
    {
        boolean open = buffer.readBoolean();
        if (!buffer.readBoolean())
        {
            return new ArmyActionPacket(open, null, null, null, "");
        }
        NpcArmy.Action action = buffer.readEnum(NpcArmy.Action.class);
        UUID subject = buffer.readUUID();
        UUID other = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ArmyActionPacket(open, action, subject, other, buffer.readUtf(NpcArmy.NAME_LENGTH));
    }

    public static void handle(ArmyActionPacket packet, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player == null)
        {
            return;
        }
        if (packet.action != null && packet.subject != null && ArmyThrottle.allowAction(player))
        {
            NpcArmy.act(player, packet.action, packet.subject, packet.other, packet.text);
        }
        ArmyThrottle.requestList(player, packet.open);
    }
}
