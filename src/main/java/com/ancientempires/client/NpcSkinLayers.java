package com.ancientempires.client;

import com.ancientempires.AncientEmpires;
import com.ancientempires.entity.npc.NpcEntity;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The player model already draws the hat, jacket, sleeves, and pants. This
 * hides a part when that skin has no pixels in its overlay, so a 64 by 32
 * skin keeps a hat that has pixels and does not grow a body shell from the
 * wrapped lower half. When 3D Skin Layers is loaded, the same parts receive
 * that mod's wide-arm meshes inside the distance it uses for players.
 */
final class NpcSkinLayers
{
    private static final String MOD_ID = "skinlayers3d";
    private static final int SKIN = 64;
    private static final int LEGACY_HEIGHT = 32;
    private static final int DEFAULT_DISTANCE = 14;

    private static final Map<ResourceLocation, Skin> SKINS = new HashMap<>();
    private static final Map<ResourceLocation, Meshes> MESHES = new HashMap<>();

    private static boolean probed;
    private static Bridge bridge;

    private NpcSkinLayers()
    {
    }

    static void apply(NpcModel model, NpcEntity entity, ResourceLocation texture)
    {
        Skin skin = skin(texture);
        Bridge layers = bridge();
        if (layers == null)
        {
            model.hat.visible = skin.hat;
            model.jacket.visible = skin.jacket;
            model.leftSleeve.visible = skin.leftSleeve;
            model.rightSleeve.visible = skin.rightSleeve;
            model.leftPants.visible = skin.leftPants;
            model.rightPants.visible = skin.rightPants;
            return;
        }
        // Null when this NPC keeps the vanilla overlay: far away, not a 64x64 skin, or no config.
        Settings settings = skin.classic ? layers.settings(entity) : null;
        Meshes meshes = settings == null ? null : meshes(texture, layers, settings.fastRender);
        boolean skull = settings != null && settings.hidesHead(entity);
        boolean hideHat = settings != null && !settings.compatibility && settings.enableHat && skull;
        Object hat = settings == null || !settings.enableHat || skull || meshes == null ? null : meshes.hat;
        layers.wear(0, model.hat, skin.hat && !hideHat, hat, layers.headOffset);
        layers.wear(1, model.jacket, skin.jacket, part(settings != null && settings.enableJacket, meshes, meshes == null ? null : meshes.jacket), layers.bodyOffset);
        layers.wear(2, model.leftSleeve, skin.leftSleeve, part(settings != null && settings.enableLeftSleeve, meshes, meshes == null ? null : meshes.leftSleeve), layers.leftArmOffset);
        layers.wear(3, model.rightSleeve, skin.rightSleeve, part(settings != null && settings.enableRightSleeve, meshes, meshes == null ? null : meshes.rightSleeve), layers.rightArmOffset);
        layers.wear(4, model.leftPants, skin.leftPants, part(settings != null && settings.enableLeftPants, meshes, meshes == null ? null : meshes.leftPants), layers.leftLegOffset);
        layers.wear(5, model.rightPants, skin.rightPants, part(settings != null && settings.enableRightPants, meshes, meshes == null ? null : meshes.rightPants), layers.rightLegOffset);
    }

    static void clear()
    {
        SKINS.clear();
        MESHES.clear();
    }

    private static Object part(boolean enabled, Meshes meshes, Object mesh)
    {
        return enabled && meshes != null ? mesh : null;
    }

    private static Skin skin(ResourceLocation texture)
    {
        Skin cached = SKINS.get(texture);
        if (cached != null)
        {
            return cached;
        }
        Skin read = read(texture);
        SKINS.put(texture, read);
        return read;
    }

    private static Skin read(ResourceLocation texture)
    {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty())
        {
            return Skin.UNTOUCHED;
        }
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream))
        {
            int width = image.getWidth();
            int height = image.getHeight();
            if (width == SKIN && height == SKIN)
            {
                return new Skin(true,
                        opaque(image, 32, 0, 32, 16),
                        opaque(image, 16, 32, 24, 16),
                        opaque(image, 48, 48, 16, 16),
                        opaque(image, 40, 32, 16, 16),
                        opaque(image, 0, 48, 16, 16),
                        opaque(image, 0, 32, 16, 16));
            }
            if (width == SKIN && height == LEGACY_HEIGHT)
            {
                return new Skin(false, opaque(image, 32, 0, 32, 16), false, false, false, false, false);
            }
            return Skin.UNTOUCHED;
        }
        catch (IOException | RuntimeException exception)
        {
            return Skin.UNTOUCHED;
        }
    }

    /**
     * Same test 3D Skin Layers uses: a pixel counts when its alpha is not zero.
     * The rectangles are the unfolded overlay boxes on a 64 by 64 skin.
     */
    private static boolean opaque(NativeImage image, int u, int v, int width, int height)
    {
        int xEnd = Math.min(image.getWidth(), u + width);
        int yEnd = Math.min(image.getHeight(), v + height);
        for (int y = Math.max(0, v); y < yEnd; y++)
        {
            for (int x = Math.max(0, u); x < xEnd; x++)
            {
                if (image.getLuminanceOrAlpha(x, y) != 0)
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static Meshes meshes(ResourceLocation texture, Bridge layers, boolean fastRender)
    {
        Meshes cached = MESHES.get(texture);
        if (cached != null && cached.fastRender == fastRender)
        {
            return cached;
        }
        Meshes built = build(texture, layers, fastRender);
        MESHES.put(texture, built);
        return built;
    }

    private static Meshes build(ResourceLocation texture, Bridge layers, boolean fastRender)
    {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty())
        {
            return Meshes.empty(fastRender);
        }
        try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream))
        {
            if (image.getWidth() != SKIN || image.getHeight() != SKIN)
            {
                return Meshes.empty(fastRender);
            }
            return new Meshes(fastRender,
                    layers.mesh(image, 8, 8, 8, 32, 0, false, 0.6F),
                    layers.mesh(image, 8, 12, 4, 16, 32, true, 0.0F),
                    layers.mesh(image, 4, 12, 4, 48, 48, true, -2.0F),
                    layers.mesh(image, 4, 12, 4, 40, 32, true, -2.0F),
                    layers.mesh(image, 4, 12, 4, 0, 48, true, 0.0F),
                    layers.mesh(image, 4, 12, 4, 0, 32, true, 0.0F));
        }
        catch (IOException | RuntimeException exception)
        {
            return Meshes.empty(fastRender);
        }
    }

    private static Bridge bridge()
    {
        if (probed)
        {
            return bridge;
        }
        probed = true;
        if (!ModList.get().isLoaded(MOD_ID))
        {
            return null;
        }
        try
        {
            bridge = Bridge.open();
        }
        catch (ReflectiveOperationException | LinkageError exception)
        {
            AncientEmpires.LOGGER.warn("3D Skin Layers is loaded, but NPC outer layers stayed on the vanilla skin.", exception);
            bridge = null;
        }
        return bridge;
    }

    /**
     * One texture's overlay. {@code classic} is a 64 by 64 image, the only size
     * that can take a thick mesh. A 64 by 32 image records the hat only.
     */
    private static final class Skin
    {
        private static final Skin UNTOUCHED = new Skin(false, true, true, true, true, true, true);

        private final boolean classic;
        private final boolean hat;
        private final boolean jacket;
        private final boolean leftSleeve;
        private final boolean rightSleeve;
        private final boolean leftPants;
        private final boolean rightPants;

        private Skin(boolean classic, boolean hat, boolean jacket, boolean leftSleeve, boolean rightSleeve, boolean leftPants, boolean rightPants)
        {
            this.classic = classic;
            this.hat = hat;
            this.jacket = jacket;
            this.leftSleeve = leftSleeve;
            this.rightSleeve = rightSleeve;
            this.leftPants = leftPants;
            this.rightPants = rightPants;
        }
    }

    private static final class Meshes
    {
        private final boolean fastRender;
        private final Object hat;
        private final Object jacket;
        private final Object leftSleeve;
        private final Object rightSleeve;
        private final Object leftPants;
        private final Object rightPants;

        private Meshes(boolean fastRender, Object hat, Object jacket, Object leftSleeve, Object rightSleeve, Object leftPants, Object rightPants)
        {
            this.fastRender = fastRender;
            this.hat = hat;
            this.jacket = jacket;
            this.leftSleeve = leftSleeve;
            this.rightSleeve = rightSleeve;
            this.leftPants = leftPants;
            this.rightPants = rightPants;
        }

        private static Meshes empty(boolean fastRender)
        {
            return new Meshes(fastRender, null, null, null, null, null, null);
        }
    }

    /**
     * 3D Skin Layers' config, read once per client tick instead of once per NPC
     * per frame. A change in that mod's settings shows up on the next tick.
     */
    private static final class Settings
    {
        private final boolean enableHat;
        private final boolean enableJacket;
        private final boolean enableLeftSleeve;
        private final boolean enableRightSleeve;
        private final boolean enableLeftPants;
        private final boolean enableRightPants;
        private final boolean fastRender;
        private final boolean compatibility;
        private final double distanceSqr;
        /** Helmets that hide the head layer. Null when that mod has no such list. */
        private final Set<?> hiddenHeads;

        private Settings(boolean enableHat, boolean enableJacket, boolean enableLeftSleeve, boolean enableRightSleeve, boolean enableLeftPants, boolean enableRightPants, boolean fastRender, boolean compatibility, double distanceSqr, Set<?> hiddenHeads)
        {
            this.enableHat = enableHat;
            this.enableJacket = enableJacket;
            this.enableLeftSleeve = enableLeftSleeve;
            this.enableRightSleeve = enableRightSleeve;
            this.enableLeftPants = enableLeftPants;
            this.enableRightPants = enableRightPants;
            this.fastRender = fastRender;
            this.compatibility = compatibility;
            this.distanceSqr = distanceSqr;
            this.hiddenHeads = hiddenHeads;
        }

        private boolean hidesHead(NpcEntity entity)
        {
            if (this.hiddenHeads == null || this.hiddenHeads.isEmpty())
            {
                return false;
            }
            Item worn = entity.getItemBySlot(EquipmentSlot.HEAD).getItem();
            return this.hiddenHeads.contains(worn);
        }
    }

    /**
     * Reflection into 3D Skin Layers. The mod is optional, so none of its types
     * are linked at compile time. The mesh arguments match its wide-arm player path.
     */
    private static final class Bridge
    {
        private final Object helper;
        private final Method create3DMesh;
        private final Method setInjectedMesh;
        private final Object emptyMesh;
        private final Object headOffset;
        private final Object bodyOffset;
        private final Object leftArmOffset;
        private final Object rightArmOffset;
        private final Object leftLegOffset;
        private final Object rightLegOffset;
        private final Field configField;
        private final Field hideHeadLayers;
        private Field renderDistanceLod;
        private Field compatibilityMode;
        private Field fastRender;
        private Field enableHat;
        private Field enableJacket;
        private Field enableLeftSleeve;
        private Field enableRightSleeve;
        private Field enableLeftPants;
        private Field enableRightPants;
        private boolean injectBroken;
        private boolean configBroken;
        private Settings settings;
        private long settingsTick = Long.MIN_VALUE;
        /** What each of the six overlay parts last received, indexed as in {@link NpcSkinLayers#apply}. */
        private final ModelPart[] lastParts = new ModelPart[6];
        private final Object[] lastMeshes = new Object[6];
        private final Object[] lastOffsets = new Object[6];

        private Bridge(Object helper, Method create3DMesh, Method setInjectedMesh, Object emptyMesh, Object headOffset, Object bodyOffset, Object leftArmOffset, Object rightArmOffset, Object leftLegOffset, Object rightLegOffset, Field configField, Field hideHeadLayers)
        {
            this.helper = helper;
            this.create3DMesh = create3DMesh;
            this.setInjectedMesh = setInjectedMesh;
            this.emptyMesh = emptyMesh;
            this.headOffset = headOffset;
            this.bodyOffset = bodyOffset;
            this.leftArmOffset = leftArmOffset;
            this.rightArmOffset = rightArmOffset;
            this.leftLegOffset = leftLegOffset;
            this.rightLegOffset = rightLegOffset;
            this.configField = configField;
            this.hideHeadLayers = hideHeadLayers;
        }

        private static Bridge open() throws ReflectiveOperationException
        {
            Class<?> api = Class.forName("dev.tr7zw.skinlayers.api.SkinLayersAPI");
            Object helper = api.getMethod("getMeshHelper").invoke(null);
            Class<?> helperType = Class.forName("dev.tr7zw.skinlayers.api.MeshHelper");
            Method create = helperType.getMethod("create3DMesh", NativeImage.class, int.class, int.class, int.class, int.class, int.class, boolean.class, float.class);
            Class<?> meshType = Class.forName("dev.tr7zw.skinlayers.api.Mesh");
            Object empty = meshType.getField("EMPTY").get(null);
            Class<?> offsetType = Class.forName("dev.tr7zw.skinlayers.api.OffsetProvider");
            Method inject = Class.forName("dev.tr7zw.skinlayers.accessor.ModelPartInjector").getMethod("setInjectedMesh", meshType, offsetType);
            Class<?> modBase = Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase");
            Field configField = modBase.getField("config");
            Field hidden = null;
            try
            {
                hidden = modBase.getField("hideHeadLayers");
            }
            catch (NoSuchFieldException exception)
            {
                hidden = null;
            }
            return new Bridge(helper, create, inject, empty,
                    offsetType.getField("HEAD").get(null),
                    offsetType.getField("BODY").get(null),
                    offsetType.getField("LEFT_ARM").get(null),
                    offsetType.getField("RIGHT_ARM").get(null),
                    offsetType.getField("LEFT_LEG").get(null),
                    offsetType.getField("RIGHT_LEG").get(null),
                    configField, hidden);
        }

        /**
         * This tick's settings when the NPC is inside that mod's mesh distance,
         * otherwise null so the vanilla overlay draws.
         */
        private Settings settings(NpcEntity entity)
        {
            if (this.configBroken)
            {
                return null;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null)
            {
                return null;
            }
            Camera camera = minecraft.gameRenderer.getMainCamera();
            if (camera == null)
            {
                return null;
            }
            long tick = minecraft.level.getGameTime();
            if (tick != this.settingsTick)
            {
                this.settingsTick = tick;
                this.settings = this.readSettings();
            }
            Settings settings = this.settings;
            if (settings == null || entity.distanceToSqr(camera.getPosition()) > settings.distanceSqr)
            {
                return null;
            }
            return settings;
        }

        private Settings readSettings()
        {
            try
            {
                Object config = this.configField.get(null);
                if (config == null)
                {
                    return null;
                }
                this.bind(config);
                int distance = this.renderDistanceLod == null ? DEFAULT_DISTANCE : this.renderDistanceLod.getInt(config);
                Object hidden = this.hideHeadLayers == null ? null : this.hideHeadLayers.get(null);
                return new Settings(
                        flag(this.enableHat, config, true),
                        flag(this.enableJacket, config, true),
                        flag(this.enableLeftSleeve, config, true),
                        flag(this.enableRightSleeve, config, true),
                        flag(this.enableLeftPants, config, true),
                        flag(this.enableRightPants, config, true),
                        flag(this.fastRender, config, true),
                        flag(this.compatibilityMode, config, false),
                        (double) distance * (double) distance,
                        hidden instanceof Set<?> set ? set : null);
            }
            catch (IllegalAccessException | RuntimeException exception)
            {
                this.configBroken = true;
                AncientEmpires.LOGGER.warn("3D Skin Layers is loaded, but NPC outer layers stayed on the vanilla skin.", exception);
                return null;
            }
        }

        /**
         * Shows or hides one overlay part and attaches its mesh. The model is
         * shared by every NPC, so the attach is skipped when the part already
         * holds the same mesh and offset from the previous draw. A rebuilt model
         * after a resource reload has new parts, which resets the record.
         */
        private void wear(int slot, ModelPart part, boolean show, Object mesh, Object offset)
        {
            part.visible = show;
            Object injected = show ? mesh : null;
            Object injectedOffset = show && mesh != null ? offset : null;
            if (this.lastParts[slot] == part && this.lastMeshes[slot] == injected && this.lastOffsets[slot] == injectedOffset)
            {
                return;
            }
            this.inject(part, injected, injectedOffset);
            this.lastParts[slot] = part;
            this.lastMeshes[slot] = injected;
            this.lastOffsets[slot] = injectedOffset;
        }

        private void bind(Object config) throws IllegalAccessException
        {
            if (this.enableHat != null)
            {
                return;
            }
            Class<?> type = config.getClass();
            this.renderDistanceLod = field(type, "renderDistanceLOD");
            this.compatibilityMode = field(type, "compatibilityMode");
            this.fastRender = field(type, "fastRender");
            this.enableHat = field(type, "enableHat");
            this.enableJacket = field(type, "enableJacket");
            this.enableLeftSleeve = field(type, "enableLeftSleeve");
            this.enableRightSleeve = field(type, "enableRightSleeve");
            this.enableLeftPants = field(type, "enableLeftPants");
            this.enableRightPants = field(type, "enableRightPants");
        }

        private static Field field(Class<?> type, String name)
        {
            try
            {
                return type.getField(name);
            }
            catch (NoSuchFieldException exception)
            {
                return null;
            }
        }

        private static boolean flag(Field field, Object config, boolean fallback) throws IllegalAccessException
        {
            if (field == null)
            {
                return fallback;
            }
            return field.getBoolean(config);
        }

        private Object mesh(NativeImage image, int width, int height, int depth, int textureU, int textureV, boolean topPivot, float rotationOffset)
        {
            try
            {
                Object mesh = this.create3DMesh.invoke(this.helper, image, width, height, depth, textureU, textureV, topPivot, rotationOffset);
                if (mesh == null || mesh == this.emptyMesh)
                {
                    return null;
                }
                return mesh;
            }
            catch (IllegalAccessException | InvocationTargetException exception)
            {
                return null;
            }
        }

        private void inject(ModelPart part, Object mesh, Object offset)
        {
            if (this.injectBroken)
            {
                return;
            }
            try
            {
                this.setInjectedMesh.invoke(part, mesh, offset);
            }
            catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException exception)
            {
                this.injectBroken = true;
                AncientEmpires.LOGGER.warn("3D Skin Layers could not attach to the NPC model. Outer layers stay on the vanilla skin.", exception);
            }
        }
    }
}
