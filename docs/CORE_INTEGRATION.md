# ZeroMods Core integration

Flux Pylons bundles the matching ZeroMods Core adapter on every supported loader and Minecraft version. The loader selects one compatible Core version when several ZeroMods mods are installed. Core classes are not copied or shaded into Flux's namespace.

## Ownership

- Core's `ManagedNetwork` owns network identity, owner, membership, node membership and access checks. `QFNetwork` delegates to that model.
- Flux keeps its existing saved-world codec, numeric network IDs, password verification, beam preferences and per-dimension indexes. These adapt Flux's wireless networks to the shared model without changing existing worlds.
- Core owns fair energy distribution and proportional allocation. Flux supplies receiver limits, actual transfer callbacks and its tick scheduling.
- Core owns tutorial playback, navigation, the timeline, screen fitting and item-model previews. Flux supplies the lesson content and artwork.
- Core supplies the common UI palette. Flux owns its gadget layout and mod-specific status colors.

Flux network assignment is explicit through the gadget. Physical adjacency, field linking and Field Emitters' connection reconciliation are not Flux network rules. Packet handlers and server-side validation remain in Flux because their actions and permissions are specific to the mod.

## Building

Place a matching ZeroMods Core checkout in `../ZeroMods-Core`. The Gradle composite build selects the adapter for this branch and packages it as a nested mod. The dependency range declares the minimum compatible Core version; keep the bundled version and that minimum aligned when adopting new APIs or required fixes.
