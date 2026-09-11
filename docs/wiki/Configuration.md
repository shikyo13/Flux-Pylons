# Configuration

Start the game or server once to generate the configuration files, then close it before editing files directly.

| File | Controls |
| --- | --- |
| `<world>/serverconfig/quantumflux-server.toml` | Energy capacity, range, transfer rate, tick interval, connection limits and upgrade balance. |
| `config/quantumflux-client.toml` | Local rendering, HUD and sound preferences. |

The `quantumflux` filenames are retained so existing worlds and settings continue to work. The mod is named Flux Pylons.

In multiplayer, the server's settings determine machine delivery and pylon limits. The gadget displays effective values; use those readings when a pack differs from this wiki's defaults.

## Appearance

Use the gadget's Settings tab for network colors and beam options. Client settings control local rendering and HUD preferences. Adjust these if another mod's overlay occupies the same part of the screen.

## Sound

Pylon hum uses Minecraft's **Blocks** volume. The client setting `audio.pylonHumVolume` defaults to `0.15`; set it to `0` to mute the hum. Powered idle pylons are quieter, and recent delivery raises the volume smoothly.

Gadget toggle sounds use the **Players** volume. Turn on Minecraft subtitles for named sound cues.

