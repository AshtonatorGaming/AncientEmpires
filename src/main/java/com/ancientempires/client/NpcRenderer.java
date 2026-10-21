package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.config.AncientEmpiresClientConfig;
import com.ancientempires.entity.npc.NpcEntity;
import com.ancientempires.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Wide player model so a held weapon and armor are visible. The texture is the
 * vanilla Steve skin until NPCs have their own. Attack and idle poses come from
 * Better Combat when that mod and Player Animator are both loaded.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NpcRenderer extends HumanoidMobRenderer<NpcEntity, NpcModel>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("textures/entity/player/wide/steve.png");
    private boolean heldItemsCollapsed;

    public NpcRenderer(EntityRendererProvider.Context context)
    {
        super(context, new NpcModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer(new NpcHeldItemLayer(this, context.getItemInHandRenderer()));
        this.keepSingleHeldItemLayer();
    }

    @Override
    public void render(NpcEntity entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight)
    {
        if (!this.heldItemsCollapsed)
        {
            this.heldItemsCollapsed = true;
            this.keepSingleHeldItemLayer();
        }
        NpcSkinLayers.apply(this.getModel(), entity, this.getTextureLocation(entity));
        float previousShadow = this.shadowRadius;
        this.shadowRadius = 0.5F * entity.getBodyScale();
        try
        {
            super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
        }
        finally
        {
            this.shadowRadius = previousShadow;
        }
    }

    /**
     * The hitbox uses the same factor, so a dwarf and a giant match their boxes.
     * The inventory preview then fits that box in the frame.
     */
    @Override
    protected void scale(NpcEntity entity, PoseStack pose, float partialTick)
    {
        float bodyScale = entity.getBodyScale();
        if (bodyScale != 1.0F)
        {
            pose.scale(bodyScale, bodyScale, bodyScale);
        }
    }

    private void keepSingleHeldItemLayer()
    {
        RenderLayer<NpcEntity, NpcModel> kept = null;
        for (RenderLayer<NpcEntity, NpcModel> layer : this.layers)
        {
            if (layer instanceof NpcHeldItemLayer)
            {
                kept = layer;
                break;
            }
        }
        if (kept == null)
        {
            return;
        }
        RenderLayer<NpcEntity, NpcModel> single = kept;
        this.layers.removeIf(layer -> layer instanceof ItemInHandLayer && layer != single);
    }

    /**
     * World nametags stay off unless the client config enables them. The
     * inventory preview sets {@link NpcEntity#isNameplateSuppressed()} for the
     * draw, so the plate is skipped there even when the setting is on.
     */
    @Override
    protected boolean shouldShowName(NpcEntity entity)
    {
        if (entity.isNameplateSuppressed() || !AncientEmpiresClientConfig.showNametags)
        {
            return false;
        }
        if (entity.getCustomName() == null)
        {
            return false;
        }
        double distance = this.entityRenderDispatcher.distanceToSqr(entity);
        float range = entity.isDiscrete() ? 32.0F : 64.0F;
        return distance < (double) (range * range);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity)
    {
        ResourceLocation skin = NpcEntity.parseSkinId(entity.getSkinId());
        return skin != null ? skin : TEXTURE;
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.NPC.get(), NpcRenderer::new);
    }

    @SubscribeEvent
    public static void reloadSkinLayers(RegisterClientReloadListenersEvent event)
    {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>()
        {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler)
            {
                return null;
            }

            @Override
            protected void apply(Void prepared, ResourceManager resourceManager, ProfilerFiller profiler)
            {
                NpcSkinLayers.clear();
            }
        });
    }

    /**
     * {@code RenderNameTagEvent} can force a plate even when
     * {@link #shouldShowName} returns false. Deny that for a hidden NPC.
     */
    @Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    private static final class Nameplates
    {
        private Nameplates()
        {
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void onNameTag(RenderNameTagEvent event)
        {
            if (!(event.getEntity() instanceof NpcEntity npc))
            {
                return;
            }
            if (npc.isNameplateSuppressed() || !AncientEmpiresClientConfig.showNametags)
            {
                event.setResult(Event.Result.DENY);
            }
        }
    }
}
