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
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@EventBusSubscriber
public final class GameTestRegistrar {
    private static final String TEST_CLASS = "dev.marblegate.letemburn.gametest.LetEmBurnGameTests";

    private GameTestRegistrar() {}

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        try {
            event.register(Class.forName(TEST_CLASS, false, GameTestRegistrar.class.getClassLoader()));
        } catch (ClassNotFoundException ignored) {
            // The dedicated GameTest source set is deliberately absent from production jars.
        }
    }
}
