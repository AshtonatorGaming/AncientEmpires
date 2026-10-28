/**
 * Packets, on one channel ({@link com.ancientempires.network.ModNetwork}): only
 * what a container's slot clicks and buttons cannot carry. The NPC's name and
 * skin from its screen, the standings screen, and the worksite bounds screen.
 * A packet that changes the world is checked again on the server (reach,
 * ownership, limits). Bump {@code ModNetwork.PROTOCOL} when a packet is added
 * or changes shape.
 */
package com.ancientempires.network;
