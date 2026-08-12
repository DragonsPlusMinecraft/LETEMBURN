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

package dev.marblegate.letemburn.gametest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@EventBusSubscriber
public final class GameTestRegistrar {
    private static final String TEST_PACKAGE = "dev.marblegate.letemburn.gametest.";

    private GameTestRegistrar() {}

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        register(event, TEST_PACKAGE + "LetEmBurnGameTests");
        register(event, TEST_PACKAGE + "PayloadEnvelopeGameTests");
        if (ModList.get().isLoaded("ballistix")) {
            register(event, TEST_PACKAGE + "BallistixPayloadGameTests");
        }
        if (ModList.get().isLoaded("mekanism")) {
            register(event, TEST_PACKAGE + "MekanismPayloadGameTests");
        }
        if (ModList.get().isLoaded("draconicevolution")) {
            register(event, TEST_PACKAGE + "DraconicPayloadGameTests");
        }
        if (ModList.get().isLoaded("mekanism") && ModList.get().isLoaded("draconicevolution")) {
            register(event, TEST_PACKAGE + "DraconicMekanismPayloadGameTests");
        }
        if (ModList.get().isLoaded("mekanism") && ModList.get().isLoaded("ballistix")) {
            register(event, TEST_PACKAGE + "BallistixMekanismPayloadGameTests");
        }
    }

    private static void register(RegisterGameTestsEvent event, String className) {
        try {
            event.register(Class.forName(className, false, GameTestRegistrar.class.getClassLoader()));
        } catch (ClassNotFoundException ignored) {
            // The dedicated GameTest source set is deliberately absent from production jars.
        }
    }
}
