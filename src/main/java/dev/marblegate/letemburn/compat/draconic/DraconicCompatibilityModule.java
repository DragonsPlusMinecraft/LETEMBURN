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

package dev.marblegate.letemburn.compat.draconic;

import dev.marblegate.letemburn.compat.core.CompatibilityModule;
import dev.marblegate.letemburn.compat.core.ImpactPayloadRegistry;

public final class DraconicCompatibilityModule implements CompatibilityModule {
    @Override
    public void register() {
        ImpactPayloadRegistry.INSTANCE.register(DraconicReactorImpactAdapter.INSTANCE);
    }
}
