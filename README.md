# Create: Frogport Networks

A [Create](https://www.curseforge.com/minecraft/mc-mods/create) addon for Minecraft 1.21.1 (NeoForge) that brings **real computer networking** to your factory. Instead of running belts and chutes across the whole base, you wire machines together with **network cables**, hand out **IP addresses**, and move items around the way a server room would — routers, storage drives, terminals and all.

> ⚠️ **Work in progress.** This mod is still in active development and isn't released anywhere yet. Most blocks are creative-only for now, things may change, and bugs are expected.

## The idea

Create is fantastic at *making* things, but moving items across a big base quickly turns into a spaghetti of belts or chains. Frogport Networks takes the opposite approach: lay down cables like network wiring, give every device an address, and let the network do the routing. If you've ever set up a home network — DHCP, static IPs, subnets — you'll feel right at home. If you haven't, the defaults just work.

## How it works

### Cables & networks
Cables are thin strips that lie flat on the surface you place them on (floor, wall, ceiling). Each cable has a **color**, and **the color is the network** — green cables form one network, blue cables another, and they stay completely separate unless you deliberately link them. A single block can carry up to four strands, so you can run several networks side by side.

You place a plain cable and it picks the next free color automatically. Want a specific one? Shift-right-click an existing strand to "tune" your cable to that run, or right-click any strand with a **dye** to recolor the whole connected line at once. Left-click removes just the strand you're aiming at.

### Routers & addressing
A **Router** is the heart of a network. It needs **rotational power** (it's a Create kinetic block, so give it a shaft) — no rotation, no network. Once it's spinning it hands out IP addresses by **DHCP**, automatically, lowest free address first. Every device can also be given a **static IP** if you'd rather assign them yourself.

### Storage: NAS & disks
The **NAS** (network-attached storage) is your mass storage, AE2-style. It holds **disks** in its bays, and the items live *inside the disks* — pull a disk and its contents come with it. Disks come in four tiers (16k → 1M items), and a NAS aggregates all the disks plugged into it.

### Terminal
The **Terminal** is your window into the whole network. It shows every item stored across all NAS on its network, with:
- **tabs** for "All" plus one per storage device,
- a **search bar**, plus **sorting** (most/least/A–Z/id) and **grouping** by mod or item tag,
- **withdrawing** with a click (stack on shift-click),
- and an **autocrafting queue** — right-click items to queue them up, set amounts, and send the whole batch to a Computer to craft.

### Computer & autocrafting
The **Computer** is the autocrafting brain. It's kinetic-powered (faster rotation = faster crafting) and takes **upgrade chips**:
- process chips (**Mixing, Pressing, Deploying, Milling, Haunting, Washing, Smelting, Smoking**) let it use those Create/vanilla recipe types, pulling ingredients straight from network storage;
- the **AI chip** unlocks **recursive crafting** — ask for a complex item and it'll craft all the intermediate steps it's missing, as long as the raw materials are on the network.

Hover an item in the Terminal and it tells you what it'll consume and what it needs to craft along the way.

### Moving items in and out
- The **Network Port** bridges physical item transport and the network — funnels, hoppers and especially Create's **Packager / logistics** can read the whole network's stock through it and request items.
- The **Network Bridge** is a single transfer rule: move up to N of item X from one IP to another while the destination has less than a threshold.
- The **Gateway** couples networks of different colors *over cables*, so item requests can flow between subnets you've deliberately joined.
- The **Network Monitor** gives you a read-only overview of *every* network and subnet at once.

## JEI integration (optional)

If [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) is installed, the Terminal plays nicely with it:
- press **R / U** on any item in the browser to see its recipe / uses,
- the JEI search bar and the Terminal search stay **in sync**,
- and you can **drag an item from JEI** onto the grid to queue it for crafting.


## Building from source

Requires a JDK 21. From the project root:

```bash
./gradlew build          # produces the mod jar in build/libs/
./gradlew runClient      # launch a dev client (with Create + JEI in the dev runtime)
```

If your IDE is missing libraries, `./gradlew --refresh-dependencies` refreshes the cache.

## License

Dual-licensed, mirroring Create's own model:
- **Code** — [MIT](LICENSE)
- **Assets** (everything under `src/main/resources/assets/`) — All Rights Reserved

See [`LICENSE`](LICENSE) for the full text. This mod is an independent addon and is not affiliated with or endorsed by the Create team.
