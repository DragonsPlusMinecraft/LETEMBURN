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

package dev.marblegate.letemburn.compat.core;

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.compat.vanilla.VanillaTntImpactAdapter;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import net.neoforged.fml.ModList;

public final class CompatibilityBootstrap {
    private static final Map<String, String> OPTIONAL_MODULES = Map.of(
            "ballistix", "dev.marblegate.letemburn.compat.ballistix.BallistixCompatibilityModule",
            "draconicevolution", "dev.marblegate.letemburn.compat.draconic.DraconicCompatibilityModule",
            "mekanism", "dev.marblegate.letemburn.compat.mekanism.MekanismCompatibilityModule",
            "moretnt", "dev.marblegate.letemburn.compat.moretnt.MoreTntCompatibilityModule",
            "pneumaticcraft", "dev.marblegate.letemburn.compat.pneumaticcraft.PneumaticCompatibilityModule");
    private static boolean bootstrapped;

    private CompatibilityBootstrap() {}

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        ImpactPayloadRegistry.INSTANCE.register(VanillaTntImpactAdapter.INSTANCE);
        OPTIONAL_MODULES.forEach((modId, className) -> {
            if (ModList.get().isLoaded(modId)) {
                loadModule(modId, className);
            }
        });
    }

    private static void loadModule(String modId, String className) {
        try {
            Class<?> moduleClass = Class.forName(className, true, CompatibilityBootstrap.class.getClassLoader());
            CompatibilityModule module = (CompatibilityModule) moduleClass.getDeclaredConstructor().newInstance();
            module.register();
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | ClassCastException exception) {
            throw new IllegalStateException("Failed to initialize optional compatibility for " + modId, exception);
        }
        LetEmBurn.LOGGER.info("Initialized optional compatibility for {}", modId);
    }
}
