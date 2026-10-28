/**
 * The two config files. {@link com.ancientempires.config.AncientEmpiresConfig}
 * is a SERVER config (per world, {@code serverconfig/ancientempires-server.toml},
 * synced to clients) holding every gameplay number. Each setting has a spec
 * field, a public static value the code reads, and a line in {@code bake()}
 * that copies one into the other whenever Forge loads or reloads the file;
 * keep the three together. {@link com.ancientempires.config.AncientEmpiresClientConfig}
 * holds each player's visual preferences. The player-facing reference is
 * {@code docs/config.md}.
 */
package com.ancientempires.config;
