package com.ancientempires.network;

import com.ancientempires.combat.Parry;
import com.ancientempires.combat.WeaponClassifier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: the player pressed Parry. Opens a parry with the weapon in
 * hand, as an NPC's: not with a shield up (that is blocking), a bow, or an empty
 * hand. The server times it, so timing is the same for players and NPCs.
 */
public class ParryPacket
{
    public static void encode(ParryPacket packet, FriendlyByteBuf buffer)
    {
    }

    public static ParryPacket decode(FriendlyByteBuf buffer)
    {
        return new ParryPacket();
    }

    public static void handle(ParryPacket packet, Supplier<NetworkEvent.Context> context)
    {
        ServerPlayer player = context.get().getSender();
        if (player == null || player.isSpectator() || player.isUsingItem())
        {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        if (weapon.isEmpty() || WeaponClassifier.isBow(weapon) || WeaponClassifier.isShield(weapon))
        {
            return;
        }
        // No arm swing: with Better Combat a vanilla swing can start an attack.
        Parry.start(player);
    }
}
