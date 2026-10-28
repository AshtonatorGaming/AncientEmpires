/**
 * Everything that runs only on the client: rendering, animation, and screens.
 *
 * <p>Rendering: {@link com.ancientempires.client.NpcRenderer} and
 * {@link com.ancientempires.client.NpcModel} draw the NPC on the player model,
 * with skins from {@code entity.npc.NpcSkins} (preloaded by
 * {@link com.ancientempires.client.NpcSkinPrefetch}), outer layers
 * ({@link com.ancientempires.client.NpcSkinLayers}, 3D with 3D Skin Layers),
 * held items, a bearer's banner on its back, and the status icon. Gates, flags,
 * and hidden spawners have their own renderers.
 *
 * <p>Animation: {@link com.ancientempires.client.NpcCombatAnimations} decides
 * what poses an NPC: Better Mob Combat's stack when that mod is present
 * ({@link com.ancientempires.client.NpcMobCombatClient}), otherwise
 * {@link com.ancientempires.client.NpcBetterCombatDriver}, which plays Better
 * Combat's clips by hand (Player Animator only animates real players). EMF,
 * Moonlight, Not Enough Animations, and Better Combat Particle are each reached
 * through a small compat class that does nothing when its mod is absent.
 *
 * <p>Screens: {@link com.ancientempires.client.NpcScreen} is the NPC inventory (a
 * vanilla container screen over {@code inventory.NpcMenu}); the path chooser,
 * stats sheet, and skin selector are popups over it
 * ({@link com.ancientempires.client.NpcPopupScreen}). Worksite storage,
 * worksite bounds, the spawner, and faction standings have their own screens.
 * {@link com.ancientempires.client.NpcGui} holds the shared parchment look.
 */
package com.ancientempires.client;
