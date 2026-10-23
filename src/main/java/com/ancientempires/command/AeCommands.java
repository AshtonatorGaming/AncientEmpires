package com.ancientempires.command;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcFactions;
import com.ancientempires.entity.npc.NpcStandings;
import com.ancientempires.network.ModNetwork;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /ancientempires standing}: a player's standing with every faction.
 * Anyone can read their own; admins (permission 2) read anyone's and can set,
 * adjust, or reset it.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class AeCommands
{
    private static final SimpleCommandExceptionType UNKNOWN_FACTION =
            new SimpleCommandExceptionType(Component.translatable("commands.ancientempires.standing.unknown_faction"));

    private AeCommands()
    {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event)
    {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal(AncientEmpires.MOD_ID)
                .then(Commands.literal("standing")
                        .executes(context ->
                        {
                            // A player gets the banner screen; the chat list stays for viewing others.
                            ModNetwork.sendStandings(context.getSource().getPlayerOrException());
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> show(context, EntityArgument.getPlayer(context, "player")))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("faction", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcFactions.ids(), builder))
                                                .then(Commands.argument("value", IntegerArgumentType.integer())
                                                        .executes(context -> change(context, false)))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("faction", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcFactions.ids(), builder))
                                                .then(Commands.argument("value", IntegerArgumentType.integer())
                                                        .executes(context -> change(context, true)))))
                                .then(Commands.literal("reset")
                                        .executes(AeCommands::reset)))));
    }

    /** Two lines: factions at peace with the player, then hostile ones, each best to worst. */
    private static int show(CommandContext<CommandSourceStack> context, ServerPlayer player)
    {
        NpcStandings standings = NpcStandings.get(context.getSource().getServer());
        List<String> factions = new ArrayList<>(NpcFactions.ids());
        factions.sort(Comparator.comparingInt((String id) -> -standings.get(player.getUUID(), id)).thenComparing(id -> id));
        MutableComponent friendly = Component.empty();
        MutableComponent hostile = Component.empty();
        for (String faction : factions)
        {
            int value = standings.get(player.getUUID(), faction);
            MutableComponent line = value < 0 ? hostile : friendly;
            if (!line.getSiblings().isEmpty())
            {
                line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            line.append(NpcFactions.displayName(faction).copy().withStyle(value < 0 ? ChatFormatting.RED : ChatFormatting.GREEN))
                    .append(Component.literal(" " + value).withStyle(ChatFormatting.GRAY));
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.standing.header", player.getDisplayName()), false);
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.standing.friendly", friendly), false);
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.standing.hostile", hostile), false);
        return factions.size();
    }

    private static int change(CommandContext<CommandSourceStack> context, boolean add) throws CommandSyntaxException
    {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        String faction = StringArgumentType.getString(context, "faction");
        if (NpcFactions.get(faction) == null)
        {
            throw UNKNOWN_FACTION.create();
        }
        int value = IntegerArgumentType.getInteger(context, "value");
        NpcStandings standings = NpcStandings.get(context.getSource().getServer());
        int now = add ? standings.adjust(player.getUUID(), faction, value) : standings.set(player.getUUID(), faction, value);
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.standing.changed",
                player.getDisplayName(), NpcFactions.displayName(faction), now), true);
        return now;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        NpcStandings.get(context.getSource().getServer()).reset(player.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.standing.reset", player.getDisplayName()), true);
        return 1;
    }
}
