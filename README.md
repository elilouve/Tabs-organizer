# Tabs Organizer (Forge 1.20.1)

This mod is write with ai. I don't know how to code, I can make various thing but code is something I'm not good. Everything is test and debug, the mod work fine but I can make mistake with the code, report if you have any issue with the crash log or the latest log. I do my best to patch everything inside the mod. If you have any suggestion to improve the code don't hesitate and I check to patch ! Anyway, enjoy the mod.

A **client-side** Forge mod for Minecraft **1.20.1** that cleans up the creative inventory when you have
many mods installed.

- Two stacked rows of tabs on top and two on the bottom, like the tabs of a Windows settings window
- Drag and drop tabs to reorder them, even across pages
- Merge tabs together: the merged tab gets a side panel, with an automatic **General (A-Z)** entry
- Rename tabs and merged entries
- A padlock to lock everything and avoid accidental changes
- Everything is saved in `config/tabsorganizer.json`

> The NeoForge 1.21.1 version lives on the `1.21.1-neoforge` branch.

## Requirements

- Minecraft 1.20.1
- Forge 47.x (1.20.1)
- Client side only (nothing to install on a server)

## How to use

The tabs are **locked** by default. Click the padlock above the creative window to unlock them.

| Action | How |
| --- | --- |
| Move a tab | Drag it onto an empty slot (the slot is outlined) or onto another tab (they swap places) |
| Change page while dragging | Hold the tab over the `<` or `>` arrow. A new empty page appears at the end while unlocked |
| Bring the back row to the front | Click one of its tabs |
| Merge tabs | Right-click the tab to merge, (turn the page if needed), then click the tab to merge it into. `Esc` cancels |
| Show the merged content | Select the merged tab (it shows `+n`). The panel on the left lists **General (A-Z)**, the tab itself and every merged tab |
| Take a tab out of a merge | Right-click its entry in the left panel |
| Reorder the merged tabs | Drag an entry inside the left panel |
| Rename a tab or an entry | `Shift` + right-click on it |
| More or fewer columns in the left panel | The `-` and `+` buttons at the top of the panel (each merged tab has its own setting) |
| Reset everything | Unlock, then click the reset button twice (positions, merges, names and columns) |

The padlock prevents moving, merging, renaming and resetting. Selecting tabs and entries always works.

## Configuration

`config/tabsorganizer.json` is created at the first launch. You normally never need to edit it by hand.

| Key | Meaning |
| --- | --- |
| `locked` | `true` when the tabs are locked |
| `tabsPerRow` | Tabs per row, from 1 to 5 (default 5) |
| `positions` | Saved position of every tab |
| `groups` | Merged tabs: host tab → list of merged tabs |
| `names` | Names chosen by the player |
| `sidebarColumns` | Number of columns of the left panel, per merged tab |

Deleting the file brings everything back to the default.

## Building from source

You need Java 17.

```
./gradlew build        (on Windows PowerShell: .\gradlew build)
```

The mod is created in `build/libs/tabsorganizer-forge-1.20.1-<version>.jar`.
To test it in a development game: `./gradlew runClient`.

## License

[MIT](LICENSE) © 2026 elilouve

This project uses the official Mojang mappings. See
<https://github.com/MinecraftForge/MCPConfig/blob/master/Mojang.md> for their license.

---

# Français

**Tabs Organizer** est un mod Forge 1.20.1, **uniquement côté client**, qui range le menu créatif quand on a beaucoup de mods :
deux rangées d'onglets en haut et deux en bas, déplacement des onglets par glisser-déposer (même d'une page à l'autre),
fusion d'onglets avec un panneau latéral et une entrée automatique « Général (A-Z) », renommage, et un cadenas contre
les modifications involontaires.

Les onglets sont verrouillés au départ : clique sur le cadenas au-dessus de la fenêtre pour les déverrouiller.
Glisser = déplacer, clic droit = fusionner, Maj + clic droit = renommer. Voir le tableau ci-dessus pour le détail.
Tout est sauvegardé dans `config/tabsorganizer.json`.
