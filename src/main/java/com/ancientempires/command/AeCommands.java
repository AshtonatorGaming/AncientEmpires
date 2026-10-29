package com.ancientempires.command;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcChain;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.entity.npc.NpcFactions;
import com.ancientempires.entity.npc.NpcJobGoal;
import com.ancientempires.entity.npc.NpcMorale;
import com.ancientempires.entity.npc.NpcRank;
import com.ancientempires.entity.npc.NpcStandings;
import com.ancientempires.network.ModNetwork;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.List;

/**
 * The {@code /ancientempires} command tree. Registers every sub-command:
 * {@code standing} (anyone sees their own on the banner screen; admins,
 * permission 2, read anyone's and can set, adjust, or reset it), {@code work}
 * and {@code level} (the looked-at NPC, for its owner or a creative player),
 * the admin tools {@code level <n>}, {@code rank}, {@code morale}, and
 * {@code stress} ({@link StressTest}), and {@code structure}
 * ({@link StructureCommands}). The player guide is {@code docs/commands.md}.
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
                .then(Commands.literal("work")
                        .executes(AeCommands::work))
                .then(StructureCommands.build())
                .then(Commands.literal("morale")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(NpcMorale.State.values()).map(state -> state.name().toLowerCase(Locale.ROOT)), builder))
                                .executes(context -> morale(context, 0.0D))
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 128.0D))
                                        .executes(context -> morale(context, DoubleArgumentType.getDouble(context, "radius"))))))
                .then(Commands.literal("rank")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(NpcRank.values()).map(NpcRank::id), builder))
                                .executes(AeCommands::rank)))
                .then(Commands.literal("squad")
                        .executes(AeCommands::squad))
                .then(Commands.literal("level")
                        .executes(context -> level(context, -1))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 100))
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> level(context, IntegerArgumentType.getInteger(context, "level")))))
                .then(Commands.literal("stress")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("clear")
                                .executes(AeCommands::stressClear))
                        .then(Commands.literal("report")
                                .executes(context ->
                                {
                                    for (Component line : StressTest.report(context.getSource().getLevel()))
                                    {
                                        context.getSource().sendSuccess(() -> line, false);
                                    }
                                    return 1;
                                }))
                        .then(Commands.argument("perSide", IntegerArgumentType.integer(1, StressTest.MAX_PER_SIDE))
                                .then(Commands.argument("factionA", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcFactions.ids(), builder))
                                        .then(Commands.argument("factionB", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(NpcFactions.ids(), builder))
                                                .executes(AeCommands::stress)))))
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

    /** The NPC the player is looking at within 8 blocks that they may command (owner or admin), or null. */
    @Nullable
    private static NpcEntity lookedAt(ServerPlayer player)
    {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(8.0D));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0D),
                entity -> entity instanceof NpcEntity, 64.0D);
        return hit != null && hit.getEntity() instanceof NpcEntity npc && npc.canOpenInventory(player) ? npc : null;
    }

    /**
     * Testing (admins): puts the looked-at NPC, or with a radius every NPC on
     * its side within it, straight into a morale state.
     */
    private static int morale(CommandContext<CommandSourceStack> context, double radius) throws CommandSyntaxException
    {
        NpcEntity npc = lookedAt(context.getSource().getPlayerOrException());
        NpcMorale.State state;
        try
        {
            state = NpcMorale.State.valueOf(StringArgumentType.getString(context, "state").toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception)
        {
            state = null;
        }
        if (npc == null || state == null)
        {
            context.getSource().sendFailure(Component.translatable(npc == null ? "commands.ancientempires.work.none" : "commands.ancientempires.morale.unknown"));
            return 0;
        }
        List<NpcEntity> targets = radius <= 0.0D ? List.of(npc)
                : npc.level().getEntitiesOfClass(NpcEntity.class, npc.getBoundingBox().inflate(radius), other -> other == npc || npc.isAlly(other));
        int changed = 0;
        for (NpcEntity target : targets)
        {
            changed += target.getMorale().force(state) ? 1 : 0;
        }
        int count = changed;
        String name = state.name().toLowerCase(Locale.ROOT);
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.morale", count,
                Component.translatable("gui.ancientempires.morale." + name)), false);
        return count;
    }

    /**
     * The looked-at NPC's place in the chain of command: its leader, its unit
     * (soldiers and officers, how many are here), its order (its own and the
     * one it acts on), formation, and whether it is in formation now.
     */
    private static int squad(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        NpcEntity npc = lookedAt(context.getSource().getPlayerOrException());
        if (npc == null)
        {
            context.getSource().sendFailure(Component.translatable("commands.ancientempires.work.none"));
            return 0;
        }
        NpcChain chain = npc.getChain();
        NpcEntity leader = chain.leader();
        Component leaderName = leader != null ? leader.getDisplayName()
                : Component.translatable(chain.leaderId() == null ? "commands.ancientempires.squad.no_leader" : "commands.ancientempires.squad.leader_away");
        int here = 0;
        for (UUID id : chain.members())
        {
            here += context.getSource().getLevel().getEntity(id) instanceof NpcEntity ? 1 : 0;
        }
        int present = here;
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.squad", npc.getDisplayName(), npc.getRankName(), leaderName,
                chain.soldierCount(), chain.officerCount(), present,
                Component.translatable("order.ancientempires." + chain.order().id()),
                Component.translatable("order.ancientempires." + chain.effectiveOrder().id()),
                Component.translatable("formation.ancientempires." + chain.formation().id()),
                Component.translatable(chain.formed() ? "commands.ancientempires.squad.formed" : "commands.ancientempires.squad.not_formed")), false);
        return 1;
    }

    /** Sets the looked-at NPC's rank (admins). */
    private static int rank(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        NpcEntity npc = lookedAt(context.getSource().getPlayerOrException());
        NpcRank rank = NpcRank.byName(StringArgumentType.getString(context, "rank"));
        if (npc == null || rank == null)
        {
            context.getSource().sendFailure(Component.translatable(npc == null ? "commands.ancientempires.work.none" : "commands.ancientempires.rank.unknown"));
            return 0;
        }
        npc.setRank(rank);
        context.getSource().sendSuccess(() -> Component.translatable("message.ancientempires.npc.promoted", npc.getDisplayName(), npc.getRankName()), false);
        return 1;
    }

    /** The looked-at NPC's level and XP; with a number (admins), sets the level. */
    private static int level(CommandContext<CommandSourceStack> context, int level) throws CommandSyntaxException
    {
        NpcEntity npc = lookedAt(context.getSource().getPlayerOrException());
        if (npc == null)
        {
            context.getSource().sendFailure(Component.translatable("commands.ancientempires.work.none"));
            return 0;
        }
        if (level > 0)
        {
            npc.setLevel(level);
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.level", npc.getDisplayName(),
                Component.translatable(NpcEntity.bandKey(npc.getNpcPath(), npc.getLevelBand())), npc.getLevel(),
                String.format("%.1f", npc.getXp()), String.format("%.0f", npc.getXpToNext())), false);
        return 1;
    }

    /**
     * What stops the NPC the player is looking at (within 8 blocks) from working:
     * a checklist of every condition the worker goals need. Owner or admin.
     */
    private static int work(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        NpcEntity npc = lookedAt(player);
        if (npc == null)
        {
            context.getSource().sendFailure(Component.translatable("commands.ancientempires.work.none"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.work.header", npc.getDisplayName()), false);
        for (String line : NpcJobGoal.diagnose(npc))
        {
            context.getSource().sendSuccess(() -> Component.literal(line)
                    .withStyle(line.startsWith("[ok]") ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        }
        return 1;
    }

    /** Two armies facing each other in front of the player, for profiling. */
    private static int stress(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String factionA = StringArgumentType.getString(context, "factionA");
        String factionB = StringArgumentType.getString(context, "factionB");
        if (NpcFactions.get(factionA) == null || NpcFactions.get(factionB) == null)
        {
            throw UNKNOWN_FACTION.create();
        }
        if (!StressTest.fight(factionA, factionB))
        {
            context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.stress.peace", factionA, factionB)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        Vec3 look = player.getLookAngle();
        Vec3 facing = new Vec3(look.x, 0.0D, look.z).normalize();
        Vec3 centre = player.position().add(facing.scale(20.0D));
        int spawned = StressTest.spawn(context.getSource().getLevel(), centre, facing, IntegerArgumentType.getInteger(context, "perSide"), factionA, factionB);
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.stress.spawned", spawned), true);
        return spawned;
    }

    private static int stressClear(CommandContext<CommandSourceStack> context)
    {
        int removed = StressTest.clear(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.translatable("commands.ancientempires.stress.cleared", removed), true);
        return removed;
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
