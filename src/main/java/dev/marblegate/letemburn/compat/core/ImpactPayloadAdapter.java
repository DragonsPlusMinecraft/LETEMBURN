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

import java.util.Objects;
import org.jetbrains.annotations.Nullable;

public interface ImpactPayloadAdapter {
    Probe probe(ProjectedEffectContext context, PayloadSnapshot payload);

    default ImpactStatus reserve(
            ProjectedEffectContext context,
            PayloadSnapshot payload,
            Probe probe,
            Runnable rollback) {
        if (probe.disposition() == ProbeDisposition.NOT_HANDLED) {
            return ImpactStatus.NOT_HANDLED;
        }
        if (probe.disposition() == ProbeDisposition.ARMED_BUT_BELOW_THRESHOLD) {
            return ImpactStatus.ARMED_BUT_BELOW_THRESHOLD;
        }
        String fingerprint = payload.fingerprint() + ":" + probe.fingerprintSuffix();
        return ChainReactionCoordinator.INSTANCE.reserve(
                context,
                fingerprint,
                marker -> commit(context, payload, probe, marker),
                rollback);
    }

    void commit(
            ProjectedEffectContext context,
            PayloadSnapshot payload,
            Probe probe,
            TransactionalEffect.CommitMarker marker)
            throws Exception;

    enum ProbeDisposition {
        NOT_HANDLED,
        ARMED_BUT_BELOW_THRESHOLD,
        READY
    }

    record Probe(ProbeDisposition disposition, String fingerprintSuffix, @Nullable Object attachment) {
        public Probe {
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(fingerprintSuffix, "fingerprintSuffix");
        }

        public static Probe notHandled() {
            return new Probe(ProbeDisposition.NOT_HANDLED, "not-handled", null);
        }

        public static Probe belowThreshold(String fingerprintSuffix) {
            return new Probe(ProbeDisposition.ARMED_BUT_BELOW_THRESHOLD, fingerprintSuffix, null);
        }

        public static Probe ready(String fingerprintSuffix, @Nullable Object attachment) {
            return new Probe(ProbeDisposition.READY, fingerprintSuffix, attachment);
        }

        public <T> T attachment(Class<T> type) {
            return type.cast(attachment);
        }
    }
}
