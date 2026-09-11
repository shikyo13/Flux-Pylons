# Power and status readings

Hold an active gadget and look at either half of a pylon to see its current state. Stored energy and delivered energy are separate readings: an empty buffer can mean the machines are using everything that arrives.

| Pylon status | Meaning |
| --- | --- |
| Transferring | Energy reached linked machines during the latest one-second window. |
| Waiting for power | Machines are linked, but the network has no available power or recent delivery. |
| No linked machines | Add machine links with the gadget. |
| Standby | Power is available, but no recent delivery was observed. Inspect the machine rows. |
| Network buffer | The pylon shares stored energy with the network and has no local machine links. |
| Paused by redstone | The pylon's current redstone rule disables its machine outputs. |

## Machine rows

Point at a machine row for its explanation and suggested next step.

| Machine status | What to do |
| --- | --- |
| Full | The exposed receiving storage is full. Run the machine or wait for demand. |
| Input not accepting | The machine has room but rejected energy. Inspect its mode, recipe and side settings. |
| Input unavailable | Enable an external energy input face. |
| Unloaded | Load the machine's chunk through normal play or your pack's chunk-loading system. |
| Out of range | Move the machine closer or restore enough range upgrades. |

Links to unloaded or out-of-range machines remain configured. They resume when the condition clears. Broken target blocks are removed during validation.

## Output and history

FE/t readings average the last 20 ticks. A delivery of one FE during that second appears as **0.05 FE/t**. After pausing output, the reading can briefly include earlier delivery.

The Overview graph retains up to one minute of observed output while a network is selected with an active gadget. Its peak is the highest reading in that minute. Missing readings leave gaps. Hover over the graph, or focus it with Tab and use Left and Right, to inspect samples.

Network totals include loaded pylons. The loaded/total count also tells you how many registered pylons are currently unavailable.

Bright connection beams indicate recent delivery. Faint beams show powered idle links. Beam visibility can be adjusted in the gadget.

