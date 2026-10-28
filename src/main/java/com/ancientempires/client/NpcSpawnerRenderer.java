package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.NpcSpawnerBlock;
import com.ancientempires.block.NpcSpawnerBlockEntity;
import com.ancientempires.registry.ModBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Draws a hidden NPC spawner's placeholder for admins only (creative or
 * spectator): builders see where every post and garrison sits, survival
 * players see nothing. A shown spawner is drawn by the chunk mesh instead.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NpcSpawnerRenderer implements BlockEntityRenderer<NpcSpawnerBlockEntity>
{
    private final BlockRenderDispatcher blocks;

    public NpcSpawnerRenderer(BlockEntityRendererProvider.Context context)
    {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModBlockEntities.NPC_SPAWNER.get(), NpcSpawnerRenderer::new);
    }

    @Override
    public void render(NpcSpawnerBlockEntity spawner, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
    {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.isCreative() || player.isSpectator()))
        {
            return;
        }
        BlockState state = spawner.getBlockState();
        if (state.getValue(NpcSpawnerBlock.SHOWN))
        {
            // Shown: the chunk mesh already draws it, for everyone.
            return;
        }
        this.blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), state, this.blocks.getBlockModel(state),
                1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY, RenderType.cutout());
    }
}
