package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.block.NpcSpawnerBlock;
import com.ancientempires.block.NpcSpawnerBlockEntity;
import com.ancientempires.registry.ModBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws a hidden NPC spawner's placeholder for admins only (creative or
 * spectator): builders see where every post and garrison sits, survival
 * players see nothing. A shown spawner is drawn by the chunk mesh instead.
 * Admins also see a nametag over every spawner, hidden or shown, naming who it
 * holds, as AW's spawner renderer did (1.5 up, seen through walls).
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class NpcSpawnerRenderer implements BlockEntityRenderer<NpcSpawnerBlockEntity>
{
    private final BlockRenderDispatcher blocks;
    /** Parsed labels by their synced JSON; there are only ever a few hundred kinds. */
    private final Map<String, Component> labels = new HashMap<>();

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
        this.nametag(spawner, pose, buffers);
        BlockState state = spawner.getBlockState();
        if (state.getValue(NpcSpawnerBlock.SHOWN))
        {
            // Shown: the chunk mesh already draws it, for everyone.
            return;
        }
        this.blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), state, this.blocks.getBlockModel(state),
                1.0F, 1.0F, 1.0F, light, overlay, ModelData.EMPTY, RenderType.cutout());
    }

    private void nametag(NpcSpawnerBlockEntity spawner, PoseStack pose, MultiBufferSource buffers)
    {
        String json = spawner.clientLabel();
        if (json.isEmpty())
        {
            return;
        }
        Component label = this.labels.computeIfAbsent(json, key ->
        {
            try
            {
                Component parsed = Component.Serializer.fromJson(key);
                return parsed == null ? Component.literal(key) : parsed;
            }
            catch (RuntimeException exception)
            {
                return Component.literal(key);
            }
        });
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        pose.pushPose();
        pose.translate(0.5F, 1.5F, 0.5F);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = pose.last().pose();
        float x = -font.width(label) / 2.0F;
        int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(label, x, 0.0F, 0x20FFFFFF, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
        font.drawInBatch(label, x, 0.0F, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** The nametag up the spawner sees through walls, so it must be drawn from further than block entities usually are. */
    @Override
    public int getViewDistance()
    {
        return 64;
    }
}
