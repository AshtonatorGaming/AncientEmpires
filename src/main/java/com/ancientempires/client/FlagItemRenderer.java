package com.ancientempires.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A flag item drawn as the flag: a faction's standard on AW's banner model, a
 * plain flag as a white banner. The view per hand and slot is the item model's {@code display}.
 */
public final class FlagItemRenderer extends BlockEntityWithoutLevelRenderer
{
    private static FlagItemRenderer instance;

    private ModelPart cloth;
    private ModelPart pole;
    private ModelPart bar;

    private FlagItemRenderer()
    {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static FlagItemRenderer instance()
    {
        if (instance == null)
        {
            instance = new FlagItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
    {
        if (this.cloth == null)
        {
            ModelPart banner = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.BANNER);
            this.cloth = banner.getChild("flag");
            this.pole = banner.getChild("pole");
            this.bar = banner.getChild("bar");
        }
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        String faction = tag == null ? "" : tag.getString("Faction");
        FlagRenderer.renderStandard(this.cloth, this.pole, this.bar, faction, 0.0F, pose, buffers, light, overlay);
    }
}
