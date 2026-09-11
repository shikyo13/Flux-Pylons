# Networks and the gadget

Switch the gadget on with sneak-right-click in the air. Right-click the air to open it.

| Tab | What it does |
| --- | --- |
| Networks | Create, find, join or select a network. |
| Overview | See loaded storage, output and recent output history. |
| Pylons | Inspect registered pylons, including remote or unloaded positions. |
| Members | Let the owner manage who can configure the network. |
| Settings | Set the name, color, beam appearance and access options. |

## Shared power

Loaded pylons on a network share their stored FE within one dimension. A pylon beside your generator can supply the network without having local machine links. Other pylons draw from that shared pool to feed their linked machines.

Each pylon keeps its own output limit and delivery priority. Pylons do not generate energy or keep chunks loaded. Power stored in an unloaded pylon becomes available again when its chunk loads.

## Playing with friends

Networks have an owner and a member list. Add a player as a member to let them configure your pylons. Making a network publicly visible does not by itself give everyone permission to change it. Password and visibility settings control how players find and join the network.

The gadget can show remote pylon information, but changing a pylon or its links still requires you to be nearby and have permission. Spawn protection and claim rules can prevent an action.

## Delivery priority

- **Equal:** share output among accepting machines.
- **Round-robin:** rotate the delivery order.
- **Nearest-first:** favor nearby targets.

A full machine's unused share can go to other accepting machines. Energy that no machine can accept stays in storage.

## Redstone

Open a nearby pylon's controls and cycle **Redstone** between **Ignored**, **Signal required** and **Signal pauses**. A signal at either half controls that pylon's machine outputs.

Pausing machine outputs still allows FE input and shared storage. Other loaded pylons can continue working.

