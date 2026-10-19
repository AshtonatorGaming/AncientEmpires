package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
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
        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
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
}
