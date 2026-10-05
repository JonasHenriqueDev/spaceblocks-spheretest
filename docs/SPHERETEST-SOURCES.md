# Spheretest source audit

Upstream: https://github.com/Jeija/spheretest
Revision examined: 719bd6bf (master, including merged Minetest history).
Transcription supplied by the user was read; YouTube pages were inaccessible through the browsing tool. No claim to have watched the videos.

- `client/shaders/nodes_shader/opengl_vertex.glsl`: active PLANET_KEEP_SCALE is R exp(deltaY/R), angle horizontalDistance/R; imaginary component supplies X/Z, real component minus R supplies Y, all camera relative.
- `object_shader`: same vertex formula in world space, transformed back by inverse model matrix. World objects (including held objects in other players' hands) are projected.
- `selection_shader`: same formula for selection outlines.
- `wielded_hand_shader`: first-person hand remains unprojected. `wielded_shader` is for world items.
- Water: node shader transforms the terrain before optional waving-water offsets. Minecraft fluid meshes use the terrain vertex projection, without inventing Minetest water waves.
- `src/clientmap.cpp`: nine candidate translations, choose closest per chunk; no flat frustum/occlusion culling; exclude horizontal distance greater than circumference/4 and sections below -radius.
- `src/clientiface.cpp`: requests canonical wrapped chunk addresses.
- `src/collision.cpp`: wrapped node lookup while retaining unwrapped physical collision boxes.
- `src/environment.cpp`: wrapped activation and entity visibility; altitude-dependent gravity and centrifugal term.
- `src/content_cao.cpp`: nearest of nine offsets for objects.
- `src/content_sao.cpp` and `src/game.cpp`: horizontal wrap, bottom at -radius, shift X by half circumference, reset Y to -radius+1, invert vertical velocity.
- `src/mapblock_mesh.cpp`: translate existing source mesh rather than change block storage; suppress duplicate faces on small planets.
- `src/defaultsettings.cpp`: keep_scale=true, centrifugal=true, realistic_gravity=false, fallthrough=true.

## Historical commits inspected

- 50f8a372: initial sphere shader.
- 55de24a6: keep-scale option.
- 5b209c77: wrapped chunk transmission and collisions.
- c754fed9fafea63d9ef32cf499f9a3c797e49aa4: complex exponential correction.
- 529455f4: fall through the bottom.
- dd68d762161ce9064bbb758d7a2454b0cead76c1: world objects versus hand rendering.
- 38bcb295e9176570369a4e966dd1736c9095e76d: object activation/visibility across edges.
- 493c6bc3: gravity with keep-scale.
- 96adb16b: experimental block-width correction, later reverted.
- d973e4b40dc0da723921563ff2093e105c345892: retain flat movement speed and flat speed in centrifugal term.

## Units and adaptation

Original BS=10 internal units per node is omitted: Minecraft block=one node. R=planet_radius*16; circumference=ceil((R/16)*pi)*32. Surface Y=64 is the translated sea-level origin for physical altitude. Active shader uses vertex Y minus camera Y, not altitude above sea level.

Original centrifugal update has a factor of 2 (`speed.Y += v_horizontal^2/height * dt * 2`). In blocks/tick units this is 2*v_tick^2/height per tick. Base gravity retains Minecraft's per-entity default; variable coefficient is exp(h/R) below surface and exp(-2h/R) above it. Liquid and climbing retain Minecraft behavior. No visual-speed compensation is present.

Algorithms and shader projection adapted under LGPL-2.1-or-later; original contributors credited. Minecraft models/textures/shader includes come from the running game, not copied assets. All new source is provided under LGPL-2.1-or-later; MDK license retained in TEMPLATE_LICENSE.txt.

## 0.8.0 tunnel and atlas

Rechecked `src/content_sao.cpp` (bottom passage), `src/environment.cpp` (variable gravity, air movement) and commit d973e4b40dc0da723921563ff2093e105c345892. Bottom passage is a half-map X shift with Y velocity reversal, not a new physical spherical core. Minecraft living-entity vertical air damping can be disabled for this demonstration; water, lava, elytra and flying keep their native branches. Shaft construction is a new optional Minecraft convenience and replaces blocks only when its command is invoked.

The atlas is new code. Its whole-map longitude/latitude globe compresses relief and is explicitly schematic. Its alternate near-hemisphere view uses the active camera-relative Spheretest exponential, at a virtual reference height of 64. Neither changes gameplay storage or introduces cube faces. Source/video references above remain the basis; inaccessible videos were not claimed as watched.

The catalog, manager GUI, reserved independent dimensions, periodic per-planet seed configuration and separate laboratory are new Minecraft code. The collision-free satellite probe is a vanilla marker armor stand, whose server integration uses the same gravity coefficient and `2 * flatHorizontalSpeed² / exponentialHeight` term. Its initial balance speed is `sqrt(baseGravity * coefficient * exponentialHeight / 2)`. It is an orbit test tool, not evidence of exact Keplerian dynamics, radial physical gravity or collision-safe spaceflight. No compensated visual-speed term was introduced.

## 0.8.1 local bottom view

The original fallthrough remains a half-map X displacement and Y velocity reversal. The local bottom view is an additional Minecraft adaptation, authored here: source terrain is reflected through the bottom plane in a second nearby draw pass, before applying the unchanged camera-relative exponential projection. It uses the same canonical chunks, not a global sphere or separate world storage. A ray crossing the plane continues in the opposite source chunk; vanilla mining packets address that real source block, and server reach checks use the nearby bottom representation. Source layers below the bottom are clipped from rendering/collision only while fallthrough is enabled. A blocked destination provides a temporary collision surface until the exit is mined. No Immersive Portals code is used in this addition.

Chunk rounding is retained: for R=32, the square map is 224 blocks wide, so a half-map connection is 112 blocks while pi*R is approximately 100.53. These are not mathematically exact spherical antipodes; changing the radius or formula to conceal the discrepancy would change the original projection. The local connection aligns the two shaft charts without making that claim.

The extra terrain pass combines its source lightmap with the observer's local block/sky light, so a torch in the shaft can illuminate the visible opposite terrain. This is a local rendering adaptation, not fullbright or a replacement for light propagation in canonical chunks. Both unlit charts remain dark.


## 0.8.2 calibrated radius and orientation HUD

At the user's explicit request, the chunk-rounded circumference is now authoritative: effective R = map width / (2*pi). The configured integer remains the size parameter, preserving canonical map boundaries and save storage. The complex exponential and its inverse retain their equations with effective R throughout terrain, entities, selection, particles, variable gravity and satellite calculations. This changes the original parameter choice; it is not an unchanged reproduction of the original rounding mismatch. A half-map displacement now has angle pi in the aligned horizontal section, but camera dependence, the exponential's unattainable center and torus topology remain.

The new HUD is a schematic longitude/latitude atlas using periodic source samples and a live marker. Compass labels refer to Minecraft plane axes, not global spherical poles. Its passive request shares the existing bounded forecast/scan pipeline without generating chunks. Its triangle budget is at most 576, refreshed map data at most once per 30 seconds per client. The client camera cue rises smoothly to 180-degree roll and returns upright; it does not change entity controls, physical gravity, mouse angles or server position. All HUD/menu/transition code is new adaptation, with no Immersive Portals source.

## 0.8.3 conditional bottom view and projection diagnostic

The reflected bottom terrain pass now follows the client's render distance, capped by server view distance and the finite map. Five sampled camera rays intersect the exponential image of the bottom and test the existing local shaft for opaque collision. A bounded renewable server lease requests opposite source chunks only when those tests succeed; an independent 3x3 safety area remains within four blocks of passage. Looking away, closing the shaft or losing the lease releases the extra tickets and sends authoritative periodic unload messages. Vanilla local-window unloads are kept separate because they do not understand periodic source chunks. This is new Minecraft code, not an original Spheretest feature or Immersive Portals code.

The local `/planet shader true|false` command bypasses only the spherical projection in terrain shaders and CPU-projected entities, particles, outline and inverse selection. Flat storage, periodic edges, bottom reflection, movement and physical options are unchanged. Selection must use the matching inverse to address the block drawn on screen. The diagnostic defaults to enabled on every game launch; no world setting is modified. The schematic atlas/HUD is independent.
