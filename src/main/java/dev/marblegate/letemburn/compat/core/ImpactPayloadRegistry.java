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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ImpactPayloadRegistry {
    public static final ImpactPayloadRegistry INSTANCE = new ImpactPayloadRegistry();

    private final List<ImpactPayloadAdapter> adapters = new CopyOnWriteArrayList<>();

    private ImpactPayloadRegistry() {}

    public void register(ImpactPayloadAdapter adapter) {
        if (adapters.stream().noneMatch(existing -> existing.getClass() == adapter.getClass())) {
            adapters.add(adapter);
        }
    }

    public ImpactStatus dispatch(ProjectedEffectContext context, PayloadSnapshot payload) {
        for (ImpactPayloadAdapter adapter : adapters) {
            ImpactPayloadAdapter.Probe probe;
            try {
                probe = adapter.probe(context, payload);
            } catch (RuntimeException exception) {
                LetEmBurn.LOGGER.error(
                        "Failed to probe projected payload {} with {}",
                        payload.fingerprint(),
                        adapter.getClass().getName(),
                        exception);
                return ImpactStatus.NOT_HANDLED;
            }
            if (probe.disposition() == ImpactPayloadAdapter.ProbeDisposition.NOT_HANDLED) {
                continue;
            }

            ImpactStatus status = adapter.reserve(
                    context,
                    payload,
                    probe,
                    () -> payload.restoreOuter(context.level(), context.localBlockPosition()));
            return status;
        }
        return ImpactStatus.NOT_HANDLED;
    }
}
