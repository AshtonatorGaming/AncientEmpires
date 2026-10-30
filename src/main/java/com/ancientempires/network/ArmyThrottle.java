package com.ancientempires.network;

import com.ancientempires.AncientEmpires;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Paces the army packets. Every army action or unit order is answered with
 * the army list, which walks every entity in the dimension and sends up to
 * 256 soldiers; a client sending many would do that many times a tick. A list
 * goes to a player at most every {@link #LIST_TICKS}, requests in between
 * answered together at the end of the wait, and at most
 * {@link #ACTIONS_PER_TICK} actions a tick are carried out.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class ArmyThrottle
{
    private static final int LIST_TICKS = 5;
    private static final int ACTIONS_PER_TICK = 8;
    private static final Map<UUID, Integer> LAST_SENT = new HashMap<>();
    /** Lists owed, by player: whether one of the requests asked to open the screen. */
    private static final Map<UUID, Boolean> PENDING = new HashMap<>();
    /** Per player: the tick counted, and actions in it. */
    private static final Map<UUID, int[]> ACTIONS = new HashMap<>();

    private ArmyThrottle()
    {
    }

    /** Whether the player may make one more army action this tick. */
    static boolean allowAction(ServerPlayer player)
    {
        int now = player.server.getTickCount();
        int[] count = ACTIONS.computeIfAbsent(player.getUUID(), id -> new int[] {now, 0});
        if (count[0] != now)
        {
            count[0] = now;
            count[1] = 0;
        }
        return ++count[1] <= ACTIONS_PER_TICK;
    }

    /** Sends the army list now, or once the wait since the last one is over. */
    static void requestList(ServerPlayer player, boolean open)
    {
        int now = player.server.getTickCount();
        Integer last = LAST_SENT.get(player.getUUID());
        if (last == null || now - last >= LIST_TICKS || now < last)
        {
            send(player, open || PENDING.getOrDefault(player.getUUID(), false), now);
            return;
        }
        PENDING.merge(player.getUUID(), open, Boolean::logicalOr);
    }

    private static void send(ServerPlayer player, boolean open, int now)
    {
        PENDING.remove(player.getUUID());
        LAST_SENT.put(player.getUUID(), now);
        ModNetwork.sendArmy(player, open);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty())
        {
            return;
        }
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Boolean>> owed = PENDING.entrySet().iterator();
        while (owed.hasNext())
        {
            Map.Entry<UUID, Boolean> entry = owed.next();
            Integer last = LAST_SENT.get(entry.getKey());
            if (last != null && now - last < LIST_TICKS && now >= last)
            {
                continue;
            }
            owed.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null)
            {
                LAST_SENT.put(player.getUUID(), now);
                ModNetwork.sendArmy(player, entry.getValue());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        UUID id = event.getEntity().getUUID();
        LAST_SENT.remove(id);
        PENDING.remove(id);
        ACTIONS.remove(id);
    }
}
