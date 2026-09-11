# Troubleshooting

## The pylon has power but my machine does not

1. Open the pylon's machine list. Confirm that the machine is linked and read its status or tooltip.
2. Enable an external energy input in the machine's side configuration. A machine that only exposes internal storage cannot receive wireless power.
3. Confirm the machine is in range and its chunk is loaded.
4. Read the pylon's redstone rule. **Signal required** waits for a signal; **Signal pauses** stops output while a signal is present.
5. Confirm the machine actually has demand. A full machine will not accept more energy.

## The local buffer reads zero

Read the output rate too. The pylon may be delivering everything that arrives, or drawing from another loaded pylon on its network.

## Another pylon is not sharing power

Assign both pylons to the same network. Both must be loaded and in the same dimension. Do not link one pylon to the other as a machine.

## I cannot edit a pylon

Switch on the gadget, move close to the pylon, and confirm that you are the network owner or a member. A public listing alone does not grant editing permission. Spawn protection or a claim mod can also deny an action.

## I cannot remove a buffer upgrade

Let connected machines drain storage to the server's configured base capacity first. Removing the upgrade then preserves the remaining energy.

## I cannot find the animated guide

The guide is included in version **1.2.0 and later**. Open the gadget and press **?**. Version 1.1.0 does not have that button.

## Can I include the mod in a modpack?

Yes. You may redistribute unmodified official JARs and include them in modpacks without individual permission. Keep the license and credit, and link to an official project page. Separately released modified builds or feature variants require permission. Read the [license](https://github.com/shikyo13/Flux-Pylons/blob/main/LICENSE) for the full terms.

## Report a problem

Open an [issue](https://github.com/shikyo13/Flux-Pylons/issues) with your Minecraft version, loader version, Flux Pylons version, modpack or machine mod, reproduction steps and expected result. Include a status screenshot or relevant log excerpt. Remove account information, server addresses and tokens before attaching logs.

For setup questions and feedback, join [ZeroNexus | Games & Development](https://discord.gg/NrdXnbWzGC).

