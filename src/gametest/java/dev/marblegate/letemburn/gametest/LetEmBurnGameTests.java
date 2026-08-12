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

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.LetEmBurnConfig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LetEmBurn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LetEmBurnGameTests {
    private LetEmBurnGameTests() {}

    @GameTest(template = "bootstrap", timeoutTicks = 20)
    public static void bootstrapAndConfigLoad(GameTestHelper helper) {
        if (LetEmBurnConfig.MAX_ENVELOPE_DEPTH.get() != 8
                || LetEmBurnConfig.MAX_PAYLOAD_BYTES.get() != 1_048_576) {
            helper.fail("LET!EM!BURN! defaults were not loaded");
            return;
        }
        helper.succeed();
    }
}
