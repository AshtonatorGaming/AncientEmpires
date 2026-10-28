package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.registry.ModEntities;
import com.ancientempires.siege.AwVehicles;
import com.ancientempires.siege.SiegeEngineEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws siege engine placeholders with AW's own models, rebuilt from
 * {@code aw_vehicles/models.json} (AW's model classes, part for part), placed
 * as AW's {@code RenderVehicle} did: turned by the engine's yaw, flipped
 * (-1, -1, 1), at the type's scale; the turret parts take AW's turret formula.
 */
@Mod.EventBusSubscriber(modid = AncientEmpires.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SiegeEngineRenderer extends EntityRenderer<SiegeEngineEntity>
{
    /** A baked model: its root, the parts it draws, and its turret parts by name. */
    private record Baked(ModelPart root, String[] roots, JsonArray turret, Map<String, ModelPart> parts)
    {
    }

    private final Map<String, Baked> models = new HashMap<>();

    public SiegeEngineRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        AwVehicles.models().forEach((name, json) -> this.models.put(name, bake(json.getAsJsonObject())));
    }

    private static Baked bake(JsonObject model)
    {
        MeshDefinition mesh = new MeshDefinition();
        JsonObject parts = model.getAsJsonObject("parts");
        JsonArray rootNames = model.getAsJsonArray("roots");
        String[] roots = new String[rootNames.size()];
        for (int i = 0; i < roots.length; i++)
        {
            roots[i] = rootNames.get(i).getAsString();
            add(mesh.getRoot(), roots[i], parts);
        }
        ModelPart root = LayerDefinition.create(mesh, model.get("texW").getAsInt(), model.get("texH").getAsInt()).bakeRoot();
        Map<String, ModelPart> byName = new HashMap<>();
        for (String name : roots)
        {
            collect(root.getChild(name), name, parts, byName);
        }
        return new Baked(root, roots, model.getAsJsonArray("turret"), byName);
    }

    private static void add(PartDefinition parent, String name, JsonObject parts)
    {
        JsonObject part = parts.getAsJsonObject(name);
        CubeListBuilder cubes = CubeListBuilder.create();
        for (JsonElement element : part.getAsJsonArray("cubes"))
        {
            JsonObject cube = element.getAsJsonObject();
            JsonArray box = cube.getAsJsonArray("box");
            cubes.texOffs(cube.get("u").getAsInt(), cube.get("v").getAsInt()).mirror(cube.get("mirror").getAsBoolean())
                    .addBox(box.get(0).getAsFloat(), box.get(1).getAsFloat(), box.get(2).getAsFloat(), box.get(3).getAsFloat(), box.get(4).getAsFloat(),
                            box.get(5).getAsFloat(), new CubeDeformation(cube.get("inflate").getAsFloat()));
        }
        JsonArray pivot = part.getAsJsonArray("pivot");
        JsonArray rot = part.getAsJsonArray("rot");
        PartDefinition definition = parent.addOrReplaceChild(name, cubes, PartPose.offsetAndRotation(pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(),
                pivot.get(2).getAsFloat(), rot.get(0).getAsFloat(), rot.get(1).getAsFloat(), rot.get(2).getAsFloat()));
        for (JsonElement child : part.getAsJsonArray("children"))
        {
            add(definition, child.getAsString(), parts);
        }
    }

    private static void collect(ModelPart part, String name, JsonObject parts, Map<String, ModelPart> byName)
    {
        byName.put(name, part);
        for (JsonElement child : parts.getAsJsonObject(name).getAsJsonArray("children"))
        {
            collect(part.getChild(child.getAsString()), child.getAsString(), parts, byName);
        }
    }

    @Override
    public void render(SiegeEngineEntity engine, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light)
    {
        AwVehicles.Type type = engine.vehicleType();
        Baked model = this.models.get(type.model());
        if (model == null)
        {
            return;
        }
        for (JsonElement element : model.turret())
        {
            JsonObject turret = element.getAsJsonObject();
            ModelPart part = model.parts().get(turret.get("part").getAsString());
            if (part != null)
            {
                float value = turret.get("constant").getAsFloat() + turret.get("yaw").getAsFloat() * engine.turret();
                switch (turret.get("axis").getAsInt())
                {
                    case 0 -> part.xRot = value;
                    case 1 -> part.yRot = value;
                    default -> part.zRot = value;
                }
            }
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(engine.getYRot()));
        pose.scale(-type.scale(), -type.scale(), type.scale());
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(engine)));
        for (String root : model.roots())
        {
            model.root().getChild(root).render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
        }
        pose.popPose();
        super.render(engine, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(SiegeEngineEntity engine)
    {
        return ResourceLocation.fromNamespaceAndPath(AncientEmpires.MOD_ID,
                "textures/entity/aw/vehicle/" + engine.vehicleType().texture() + "_" + (engine.material() + 1) + ".png");
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.SIEGE_ENGINE.get(), SiegeEngineRenderer::new);
    }
}
