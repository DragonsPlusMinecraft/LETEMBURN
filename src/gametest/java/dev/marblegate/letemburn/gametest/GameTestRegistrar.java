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
    private GameTestRegistrar() {}

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        event.register(LetEmBurnGameTests.class);
        event.register(PayloadEnvelopeGameTests.class);
        if (ModList.get().isLoaded("ballistix")) {
            event.register(BallistixPayloadGameTests.class);
        }
        if (ModList.get().isLoaded("mekanism")) {
            event.register(MekanismPayloadGameTests.class);
        }
        if (ModList.get().isLoaded("draconicevolution")) {
            event.register(DraconicPayloadGameTests.class);
        }
        if (ModList.get().isLoaded("mekanism") && ModList.get().isLoaded("draconicevolution")) {
            event.register(DraconicMekanismPayloadGameTests.class);
        }
        if (ModList.get().isLoaded("mekanism") && ModList.get().isLoaded("ballistix")) {
            event.register(BallistixMekanismPayloadGameTests.class);
        }
        if (ModList.get().isLoaded("ballistix") && ModList.get().isLoaded("nuclearscience")) {
            event.register(BallistixNuclearScienceGameTests.class);
        }
        if (ModList.get().isLoaded("nuclearscience")) {
            event.register(NuclearScienceFissionGameTests.class);
        }
        if (ModList.get().isLoaded("pneumaticcraft")) {
            event.register(PneumaticCraftGameTests.class);
        }
        if (ModList.get().isLoaded("moretnt")) {
            event.register(MoreTntGameTests.class);
        }
        if (ModList.get().isLoaded("moretnt") && ModList.get().isLoaded("mekanism")) {
            event.register(MoreTntMekanismGameTests.class);
        }
    }
}
