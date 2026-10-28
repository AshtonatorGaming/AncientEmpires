/**
 * Ancient Warfare's siege engines, as placeholders for now.
 *
 * <p>Structures' {@code AWVehicle} entries become
 * {@link com.ancientempires.siege.SiegeEngineEntity}: AW's vehicle type and
 * material level, turned as placed, its turret as AW left it, breakable at
 * AW's health, but not moving or firing (a later tier).
 * {@link com.ancientempires.siege.AwVehicles} reads
 * {@code assets/ancientempires/aw_vehicles/models.json}: per type its model,
 * texture, and scale; per model its part tree, turret parts, and rough size.
 * That file is AW's own model classes converted by {@code tools/aw-assets}.
 * Drawing is {@code client.SiegeEngineRenderer}.
 */
package com.ancientempires.siege;
