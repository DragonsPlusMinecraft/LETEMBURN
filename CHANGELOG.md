# LET!EM!BURN! 1.2.0

### Update

* Added impact-triggered Sable payload support for vanilla TNT, failed Draconic Evolution reactor cores, Ballistix explosives, and More Fun TNTs.
* Supported payloads now preserve their native behavior after impact, including fuse timing, explosive variants, persistent blast effects, and radiation.
* Mekanism Cardboard Boxes can now recursively wrap supported explosives without replacing the innermost payload's native effect.
* Ballistix explosions now transfer their native directional force to nearby Sable structures, including offset impacts that can produce rotation.
* PneumaticCraft machines on Sable structures can now leak or rupture after sufficiently severe impacts. Real leak pressure and flow apply bounded thrust in the opposite direction.
* Added server configuration for payload envelope limits, Draconic reactor impact speed, PneumaticCraft damage and thrust, and Ballistix blast impulse.
* Updated the supported baseline to Minecraft 1.21.1, NeoForge 21.1.248, Create 6.0.10, and Sable 2.0.3.

### Optimization

* Optimized Draconic Evolution reactor explosion annulus scanning while preserving the original affected positions, processing order, random-number consumption, and explosion results.

### Fix

* Failed Draconic reactor cores can now travel directly on Sable structures and no longer consume surrounding carrier blocks while inside a sublevel.
* Draconic reactor explosions now resolve at the payload's projected parent-world position.
* More Fun TNTs payloads now create their matching native primed entities instead of degrading into vanilla TNT.
* Impact effects are deferred until the end of the physics step and deduplicated, preventing repeated explosions from a single collision.
* Cancelled or failed native effects now restore an unconsumed payload instead of silently deleting it.
* Optional integrations are isolated so LET!EM!BURN! can load safely when their corresponding mods are absent.
