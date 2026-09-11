# Flux Pylons roadmap

## Next release

- Release the 1.2.0 animated guide and gadget layout improvements for Minecraft 1.21.1 on NeoForge.
- Address reproducible energy delivery, machine compatibility and multiplayer problems reported by players.

## Version and loader ports

1. **Minecraft 1.20.1 — Forge.** The port builds on `mc/1.20.1-forge`; gameplay and persistence tests pass. The client visual pass is next.
2. **Minecraft 1.21.1 — Fabric.** Bring the same power distribution, gadget and guide to Fabric's energy ecosystem.
3. **Minecraft 1.20.1 — Fabric.** Follow after the version backport and Fabric energy adapter are established.

The order uses the September 11, 2026 snapshot from [MC Mod Popularity](https://skilles.github.io/MCModPopularity/). It ranks 1.20.1 as the most popular version. The snapshot lists 41,424 CurseForge modpacks for 1.20.1 and 14,017 for 1.21.1, compared with 772 for 1.21.4. These are catalog counts, not active-player counts. Loader support overlaps, and its loader charts group version families.

Keep the current NeoForge build working while ports are developed. Each port needs working energy input and delivery with actual machines, persistent networks and upgrades, multiplayer permissions, and the same usable gadget and guide. Version and loader tags are added only when the corresponding build is ready.

Revisit newer 26.x versions as their technical-modpack ecosystems develop. Older versions such as 1.12.2 and 1.16.5 are not in the first porting round.

## Later ideas

- Optional electrical arcs for the existing connection beams.
- Balance changes supported by experience in real packs.

Report problems and suggestions through [GitHub issues](https://github.com/shikyo13/Flux-Pylons/issues) or [Discord](https://discord.gg/NrdXnbWzGC).
