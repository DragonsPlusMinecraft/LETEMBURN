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

package dev.marblegate.letemburn.integration.moretnt;

import dev.marblegate.letemburn.LetEmBurn;
import dev.marblegate.letemburn.common.impact.ImpactPayloadRegistry;
import dev.marblegate.letemburn.integration.ModIntegration;
import net.neoforged.fml.common.Mod;

@Mod(LetEmBurn.MOD_ID)
public final class MoreTntIntegration {
    public MoreTntIntegration() {
        if (!ModIntegration.MORE_TNT.loaded()) {
            return;
        }
        ImpactPayloadRegistry.INSTANCE.register(MoreTntImpactAdapter.INSTANCE);
        LetEmBurn.LOGGER.info("Initialized optional compatibility for {}", ModIntegration.MORE_TNT.modId());
    }
}
