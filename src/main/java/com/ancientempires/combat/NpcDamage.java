package com.ancientempires.combat;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresConfig;
import com.ancientempires.entity.npc.NpcEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * NPC-on-NPC damage scale ({@code combat.npcVsNpcDamage}): battles between
 * NPCs last longer, so lines hold, morale has time to matter, and a fight
 * can be watched. Hits, arrows, and bashes count; fights with players keep
 * their own numbers.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID)
public final class NpcDamage
{
    private NpcDamage()
    {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event)
    {
        if (AncientEmpiresConfig.npcVsNpcDamage != 1.0D && event.getEntity() instanceof NpcEntity
                && event.getSource().getEntity() instanceof NpcEntity attacker && attacker != event.getEntity())
        {
            event.setAmount((float) (event.getAmount() * AncientEmpiresConfig.npcVsNpcDamage));
        }
    }
}
