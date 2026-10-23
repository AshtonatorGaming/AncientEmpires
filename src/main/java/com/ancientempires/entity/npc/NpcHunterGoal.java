package com.ancientempires.entity.npc;

import com.ancientempires.block.HuntingLodgeBlockEntity;
import com.ancientempires.combat.NpcBowAttackGoal;
import com.ancientempires.config.AncientEmpiresConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A hunter: a Worker-path NPC with a bow, at its owner's hunting lodge. It
 * stalks game across the lodge's ground until it has a clear shot within
 * {@code hunting.shootRange}, draws, and looses real arrows (not picked back
 * up) until the animal falls, then goes to pick up what it dropped and carries
 * the meat, hides, and feathers back. Still a civilian: it runs from monsters.
 */
public final class NpcHunterGoal extends NpcWorkGoal<HuntingLodgeBlockEntity>
{
    private static final int SHOOT = 0;
    private static final int COLLECT = 1;
    private static final int DRAW_TICKS = 20;
    private static final double PICKUP_RANGE = 2.5D;

    /** Where the last kill fell, to pick up its drops. */
    @Nullable
    private BlockPos lastKill;

    public NpcHunterGoal(NpcEntity npc)
    {
        super(npc, HuntingLodgeBlockEntity.class);
    }

    @Override
    protected boolean isTool(ItemStack stack)
    {
        return stack.getItem() instanceof BowItem;
    }

    @Override
    protected boolean pickJob()
    {
        HuntingLodgeBlockEntity lodge = this.site;
        if (lodge == null)
        {
            return false;
        }
        if (this.tripToUnload(false))
        {
            return true;
        }
        if (this.lastKill != null)
        {
            BlockPos kill = this.lastKill;
            this.lastKill = null;
            return this.assign(COLLECT, kill);
        }
        LivingEntity game = lodge.claimGame(this.npc.getId(), this.npc.blockPosition());
        if (game != null)
        {
            return this.assignEntity(SHOOT, game);
        }
        return this.tripToUnload(true);
    }

    /** A clear shot within range, not just being close. */
    @Override
    protected boolean inReach(int kind, BlockPos target, @Nullable Entity creature)
    {
        if (kind == SHOOT && creature != null)
        {
            double range = AncientEmpiresConfig.huntShootRange;
            return this.npc.distanceToSqr(creature) <= range * range && this.npc.hasLineOfSight(creature);
        }
        return super.inReach(kind, target, creature);
    }

    @Override
    protected boolean swings(int kind)
    {
        return kind != SHOOT;
    }

    @Override
    protected int actionTicks(int kind, BlockPos target)
    {
        return kind == SHOOT ? DRAW_TICKS : super.actionTicks(kind, target);
    }

    @Override
    protected void onActionStart(int kind)
    {
        if (kind == SHOOT && !this.npc.isUsingItem())
        {
            this.npc.startUsingItem(InteractionHand.MAIN_HAND);
        }
    }

    @Override
    protected void onActionCancelled(int kind)
    {
        if (kind == SHOOT && this.npc.isUsingItem())
        {
            this.npc.stopUsingItem();
        }
    }

    @Override
    protected void onCreatureGone(int kind, Entity creature)
    {
        if (kind == SHOOT && creature instanceof LivingEntity living && living.isDeadOrDying())
        {
            this.lastKill = creature.blockPosition();
        }
    }

    @Override
    protected boolean act(ServerLevel level, int kind, BlockPos target)
    {
        if (kind == COLLECT)
        {
            this.collectAt(level, target, PICKUP_RANGE);
            return false;
        }
        Entity creature = this.entityTarget();
        if (kind != SHOOT || !(creature instanceof LivingEntity game) || !game.isAlive())
        {
            return false;
        }
        this.loose(level, game);
        // Keep drawing on it until it falls.
        return game.isAlive() && this.isTool(this.npc.getMainHandItem());
    }

    /** A full-draw arrow at the animal, aimed with the archers' arc correction. */
    private void loose(ServerLevel level, LivingEntity game)
    {
        if (this.npc.isUsingItem())
        {
            this.npc.stopUsingItem();
        }
        ItemStack bow = this.npc.getMainHandItem();
        if (!(bow.getItem() instanceof BowItem bowItem))
        {
            return;
        }
        AbstractArrow arrow = bowItem.customArrow(((ArrowItem) Items.ARROW).createArrow(level, new ItemStack(Items.ARROW), this.npc));
        if (arrow == null)
        {
            return;
        }
        arrow.setCritArrow(true);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        double dx = game.getX() - this.npc.getX();
        double dz = game.getZ() - this.npc.getZ();
        double dy = game.getY(0.5D) - arrow.getY();
        float speed = 3.0F;
        arrow.shoot(dx, dy + NpcBowAttackGoal.gravityLift(dx, dy, dz, speed), dz, speed, 1.0F);
        level.addFreshEntity(arrow);
        this.npc.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.npc.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
        this.wearTool();
    }
}
