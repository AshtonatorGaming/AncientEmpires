package com.ancientempires.network;

import com.ancientempires.entity.npc.NpcArmy;
import com.ancientempires.AncientEmpires;
import com.ancientempires.block.WorksiteArea;
import com.ancientempires.block.WorksiteBlockEntity;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcFactions;
import com.ancientempires.entity.npc.NpcStandings;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;

/**
 * Packets the container button click cannot carry. Slot clicks stay on the menu.
 */
public final class ModNetwork
{
    /** Bump whenever a packet is added or changes shape, so mismatched clients are refused cleanly. */
    private static final String PROTOCOL = "16";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.parse(AncientEmpires.MOD_ID + ":main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    private static int nextId;

    private ModNetwork()
    {
    }

    public static void register()
    {
        CHANNEL.messageBuilder(NpcNamePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NpcNamePacket::encode)
                .decoder(NpcNamePacket::decode)
                .consumerMainThread(NpcNamePacket::handle)
                .add();
        CHANNEL.messageBuilder(NpcSkinPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NpcSkinPacket::encode)
                .decoder(NpcSkinPacket::decode)
                .consumerMainThread(NpcSkinPacket::handle)
                .add();
        CHANNEL.messageBuilder(StandingsRequestPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StandingsRequestPacket::encode)
                .decoder(StandingsRequestPacket::decode)
                .consumerMainThread(StandingsRequestPacket::handle)
                .add();
        CHANNEL.messageBuilder(StandingsPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StandingsPacket::encode)
                .decoder(StandingsPacket::decode)
                .consumerMainThread(StandingsPacket::handle)
                .add();
        CHANNEL.messageBuilder(WorksiteBoundsPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WorksiteBoundsPacket::encode)
                .decoder(WorksiteBoundsPacket::decode)
                .consumerMainThread(WorksiteBoundsPacket::handle)
                .add();
        CHANNEL.messageBuilder(WorksiteBoundsSetPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorksiteBoundsSetPacket::encode)
                .decoder(WorksiteBoundsSetPacket::decode)
                .consumerMainThread(WorksiteBoundsSetPacket::handle)
                .add();
        CHANNEL.messageBuilder(ScannerSavePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScannerSavePacket::encode)
                .decoder(ScannerSavePacket::decode)
                .consumerMainThread(ScannerSavePacket::handle)
                .add();
        CHANNEL.messageBuilder(SoundBlockPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SoundBlockPacket::encode)
                .decoder(SoundBlockPacket::decode)
                .consumerMainThread(SoundBlockPacket::handle)
                .add();
        CHANNEL.messageBuilder(OutlinePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OutlinePacket::encode)
                .decoder(OutlinePacket::decode)
                .consumerMainThread(OutlinePacket::handle)
                .add();
        CHANNEL.messageBuilder(ArmyActionPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ArmyActionPacket::encode)
                .decoder(ArmyActionPacket::decode)
                .consumerMainThread(ArmyActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(ArmyPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ArmyPacket::encode)
                .decoder(ArmyPacket::decode)
                .consumerMainThread(ArmyPacket::handle)
                .add();
        CHANNEL.messageBuilder(TradePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TradePacket::encode)
                .decoder(TradePacket::decode)
                .consumerMainThread(TradePacket::handle)
                .add();
        CHANNEL.messageBuilder(DraftingPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DraftingPacket::encode)
                .decoder(DraftingPacket::decode)
                .consumerMainThread(DraftingPacket::handle)
                .add();
        CHANNEL.messageBuilder(DraftingPacket.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DraftingPacket.Request::encode)
                .decoder(DraftingPacket.Request::decode)
                .consumerMainThread(DraftingPacket.Request::handle)
                .add();
        CHANNEL.messageBuilder(TradePacket.Request.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TradePacket.Request::encode)
                .decoder(TradePacket.Request::decode)
                .consumerMainThread(TradePacket.Request::handle)
                .add();
        CHANNEL.messageBuilder(UnitOrderPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UnitOrderPacket::encode)
                .decoder(UnitOrderPacket::decode)
                .consumerMainThread(UnitOrderPacket::handle)
                .add();
    }

    /** Outlines blocks for one player, through walls, for a few seconds. */
    public static void outline(ServerPlayer player, List<BlockPos> blocks, OutlinePacket.Kind kind)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OutlinePacket(blocks, kind));
    }

    /** The player's army, for the command screen ({@code open}: open it; else refresh it if open). */
    public static void sendArmy(ServerPlayer player, boolean open)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ArmyPacket(open, NpcArmy.list(player)));
    }

    public static void requestArmy()
    {
        CHANNEL.sendToServer(ArmyActionPacket.request());
    }

    /** A fresh army list without opening the command screen (the wheel's unit panel). */
    public static void requestArmyRefresh()
    {
        CHANNEL.sendToServer(new ArmyActionPacket(false, null, null, null, ""));
    }

    /** One packet to one player. */
    public static void send(ServerPlayer player, Object packet)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet)
    {
        CHANNEL.sendToServer(packet);
    }

    public static void requestStandings()
    {
        CHANNEL.sendToServer(new StandingsRequestPacket());
    }

    /** Sends a player their standing with every faction, which opens the standings screen. */
    public static void sendStandings(ServerPlayer player)
    {
        NpcStandings standings = NpcStandings.get(player.server);
        List<StandingsPacket.Entry> entries = new ArrayList<>();
        for (String faction : NpcFactions.ids())
        {
            entries.add(new StandingsPacket.Entry(faction, standings.get(player.getUUID(), faction)));
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StandingsPacket(entries));
    }

    /** Opens or refreshes a player's bounds screen for a worksite. */
    public static void sendWorksiteBounds(ServerPlayer player, WorksiteBlockEntity site)
    {
        WorksiteBoundsPacket.State state = new WorksiteBoundsPacket.State(site.getBlockPos(), site.area(), site.maxSize(),
                AncientEmpiresConfig.workAreaReach, site.mayContainBlock(), !site.canResize(), site.sizeTier(), site.upgradeKind());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new WorksiteBoundsPacket(state));
    }

    public static void sendWorksiteArea(BlockPos pos, WorksiteArea area)
    {
        CHANNEL.sendToServer(new WorksiteBoundsSetPacket(pos, area));
    }

    public static void sendName(int entityId, String name)
    {
        CHANNEL.sendToServer(new NpcNamePacket(entityId, cap(name, NpcNamePacket.MAX_LENGTH)));
    }

    public static void sendSkin(int entityId, String skin)
    {
        CHANNEL.sendToServer(new NpcSkinPacket(entityId, cap(skin, NpcSkinPacket.MAX_LENGTH)));
    }

    private static String cap(String value, int max)
    {
        String text = value == null ? "" : value;
        if (text.length() > max)
        {
            return text.substring(0, max);
        }
        return text;
    }
}
