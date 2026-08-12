/*
 * Copyright (C) 2026 MarbleGate
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.marblegate.letemburn.integration.ballistix;

import ballistix.api.blast.IBlast;
import ballistix.common.block.subtype.SubtypeBlast;
import ballistix.common.settings.BallistixConfig;
import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.common.impulse.ExplosionImpulseProfile;
import dev.marblegate.letemburn.common.impulse.ExplosionImpulseProfile.Direction;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public final class BallistixImpulseProfiles {
    private static final Map<ResourceLocation, Definition> DEFINITIONS = definitions();
    private static final Set<ResourceLocation> WARNED_UNKNOWN = ConcurrentHashMap.newKeySet();

    private BallistixImpulseProfiles() {}

    public static @Nullable ExplosionImpulseProfile resolve(IBlast type) {
        ResourceLocation id = type.id();
        Definition definition = DEFINITIONS.get(id);
        if (definition == null) {
            if (WARNED_UNKNOWN.add(id)) {
                LetEmBurn.LOGGER.warn(
                        "Ballistix blast {} has no Sable impulse profile; preserving only its native effect",
                        id);
            }
            return null;
        }
        return definition.resolve(id.toString());
    }

    public static boolean hasExplicitProfile(ResourceLocation id) {
        return DEFINITIONS.containsKey(id);
    }

    public static int explicitProfileCount() {
        return DEFINITIONS.size();
    }

    static @Nullable Direction directionOf(IBlast type) {
        Definition definition = DEFINITIONS.get(type.id());
        return definition == null ? null : definition.direction();
    }

    private static Map<ResourceLocation, Definition> definitions() {
        Map<ResourceLocation, Definition> profiles = new HashMap<>();
        put(profiles, SubtypeBlast.obsidian, outward(() -> 2.0D * config().EXPLOSIVE_OBSIDIAN_SIZE.getAsDouble()));
        put(profiles, SubtypeBlast.condensive, outward(() -> 2.0D * config().EXPLOSIVE_CONDENSIVE_SIZE.getAsDouble()));
        put(profiles, SubtypeBlast.attractive, new Definition(Direction.INWARD, () -> 14.0D));
        put(profiles, SubtypeBlast.repulsive, new Definition(Direction.OUTWARD, () -> 14.0D));
        put(profiles, SubtypeBlast.incendiary, Definition.NONE);
        put(profiles, SubtypeBlast.shrapnel, Definition.NONE);
        put(profiles, SubtypeBlast.chemical, Definition.NONE);
        put(profiles, SubtypeBlast.anvil, Definition.NONE);
        put(profiles, SubtypeBlast.infestive, Definition.NONE);
        put(profiles, SubtypeBlast.debilitation, Definition.NONE);
        put(profiles, SubtypeBlast.fragmentation, Definition.NONE);
        put(profiles, SubtypeBlast.contagious, Definition.NONE);
        put(profiles, SubtypeBlast.breaching, outward(() -> 2.0D * config().EXPLOSIVE_BREACHING_SIZE.getAsDouble()));
        // attackEntities doubles its argument; these blast classes pass twice their configured size.
        put(profiles, SubtypeBlast.thermobaric, outward(() -> 4.0D * config().EXPLOSIVE_THERMOBARIC_SIZE.getAsDouble()));
        put(profiles, SubtypeBlast.sonic, outward(() -> 2.0D * config().EXPLOSIVE_SONIC_RADIUS.getAsDouble()));
        put(profiles, SubtypeBlast.antigravity, new Definition(
                Direction.UPWARD, () -> config().EXPLOSIVE_ANTIGRAVITY_RADIUS.getAsInt()));
        put(profiles, SubtypeBlast.emp, Definition.NONE);
        put(profiles, SubtypeBlast.nuclear, outward(() -> 4.0D * config().EXPLOSIVE_NUCLEAR_SIZE.getAsDouble()));
        // Ballistix 1.0.11 intentionally or accidentally uses the sonic radius for all four velocity pulses.
        put(profiles, SubtypeBlast.endothermic, outward(() -> 2.0D * config().EXPLOSIVE_SONIC_RADIUS.getAsDouble()));
        put(profiles, SubtypeBlast.exothermic, outward(() -> 2.0D * config().EXPLOSIVE_SONIC_RADIUS.getAsDouble()));
        put(profiles, SubtypeBlast.ender, outward(() -> 2.0D * config().EXPLOSIVE_ENDER_RADIUS.getAsDouble()));
        put(profiles, SubtypeBlast.hypersonic, outward(() -> 2.0D * config().EXPLOSIVE_SONIC_RADIUS.getAsDouble()));
        put(profiles, SubtypeBlast.rejuvination, Definition.NONE);
        put(profiles, SubtypeBlast.antimatter, outward(() -> 4.0D * config().EXPLOSIVE_ANTIMATTER_RADIUS.getAsDouble()));
        put(profiles, SubtypeBlast.largeantimatter, outward(() -> 4.0D * config().EXPLOSIVE_LARGEANTIMATTER_RADIUS.getAsDouble()));
        put(profiles, SubtypeBlast.darkmatter, new Definition(
                Direction.INWARD, () -> 2.0D * config().EXPLOSIVE_DARKMATTER_RADIUS.getAsDouble()));
        put(profiles, SubtypeBlast.landmine, Definition.NONE);
        return Map.copyOf(profiles);
    }

    private static Definition outward(DoubleSupplier radius) {
        return new Definition(Direction.OUTWARD, radius);
    }

    private static void put(Map<ResourceLocation, Definition> profiles, IBlast type, Definition profile) {
        profiles.put(type.id(), profile);
    }

    private static BallistixConfig config() {
        return BallistixConfig.INSTANCE;
    }

    private record Definition(Direction direction, DoubleSupplier radius) {
        private static final Definition NONE = new Definition(Direction.NONE, () -> 0.0D);

        private ExplosionImpulseProfile resolve(String id) {
            double resolvedRadius = radius.getAsDouble();
            return direction == Direction.NONE
                    ? ExplosionImpulseProfile.none(id)
                    : new ExplosionImpulseProfile(id, direction, resolvedRadius);
        }
    }
}
