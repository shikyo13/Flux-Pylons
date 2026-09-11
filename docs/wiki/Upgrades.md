# Upgrades

Open a nearby pylon with the active gadget and select **Upgrades**. Each typed slot accepts up to four matching upgrade items. The screen shows the effective limits and a preview of the next level. Shift-click installs and removes upgrades using normal inventory controls.

| Upgrade | Default pylon | With four upgrades |
| --- | ---: | ---: |
| Range | 16 blocks | 32 blocks |
| Connections | 20 machines | 28 machines |
| Transfer | 10,000 FE/t | 30,000 FE/t |
| Buffer | 100,000 FE | 1,600,000 FE |

These values use the default server configuration. Modpacks and server owners can change them.

Increasing output capacity does not create power. The network still needs enough incoming FE, and machines must accept it.

## Removing upgrades

- Range and connection upgrades can leave existing links inactive when the new limits are lower. The links remain configured.
- Buffer upgrades can be removed only after storage drains to the configured base capacity. Let machines use the stored energy first.
- Mining a pylon drops its installed upgrades. Energy stored in it is not carried by the dropped block.

Lowering the server's configured capacity preserves excess stored FE. The pylon rejects new input until that excess drains.

