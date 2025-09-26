# Escort Commands Tweaks && Automated Escort Commands

## Introduction

Manually assigning escort ships at the start of every battle is extremely tedious. When escort ships are lost in combat, you also have to manually cancel the escort tasks, otherwise the system will drag your frontline Hyperion back to escort a Dominator, which is even more frustrating.

This MOD completely prevents the vanilla system from automatically assigning escort ships by setting escort task weights to 0, and provides several 0 OP hullmods to designate escort ships and escort targets. This mod will assign escort tasks based on the installation of these hullmods.

## Hullmods

### Leader Hullmods
- **Function**: Ships equipped with this hullmod become escort targets
- **Designations**: Five designations available: A, B, C, D, E

### Escort Squadron Hullmods
- **Function**: Ships equipped with this hullmod will perform escort duties for leaders with corresponding designations
- **Designations**: A, B, C, D, E designations correspond to leaders with the same designation
- **Multiple Leader Support**: Multiple leaders with the same designation can exist on the battlefield; this mod will distribute escort ships evenly among them

### Escort Scale Hullmods
- **Installation Requirement**: Can only be installed on ships that already have a Leader hullmod equipped
- **Function**: Limits the maximum escort fleet size
- **Scale Levels**:
  - **Light Escort**: One frigate
  - **Medium Escort**: Two frigates or one destroyer
  - **Heavy Escort**: Two destroyers or one cruiser
- **Default Scale** (when this hullmod is not installed):
  - Frigates and Destroyers: Light Escort
  - Cruisers: Medium Escort
  - Capital Ships: Heavy Escort

### Support Unit Hullmod (Experimental)
- **Function**: Ships equipped with this hullmod will attempt to stay away from the front lines
- **Purpose**: Reduces instances of AI-controlled carriers charging directly at enemies

## Installation Notes

- Compatible with game version 0.98a
- Required dependency: LazyLib
- This mod can be safely added to existing saves
- Cannot be safely removed; you must uninstall this mod's hullmods from all ships before removing the mod

## Important Notes

- Automatic escort assignment begins 10 seconds after combat starts to reduce ship crowding during the initial phase
- Ships equipped with escort hullmods cannot execute capture or control task when valid escort targets are present on the battlefield. This is a deliberate task priority trade-off - without this restriction, escort ships would often be reassigned to capture objectives, neglecting their escort duties