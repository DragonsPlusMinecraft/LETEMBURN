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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DeferredEffectQueueTest {
    @Test
    void preservesReservationOrderAndDeduplicatesWithinEpoch() {
        DeferredEffectQueue<String> queue = new DeferredEffectQueue<>();
        List<String> committed = new ArrayList<>();

        assertTrue(queue.reserve(4, "first", marker -> committed.add("first"), () -> {}));
        assertTrue(queue.reserve(4, "second", marker -> committed.add("second"), () -> {}));
        assertFalse(queue.reserve(4, "first", marker -> committed.add("duplicate"), () -> {}));

        assertTrue(queue.drain().isEmpty());
        assertEquals(List.of("first", "second"), committed);
        assertFalse(queue.reserve(4, "first", marker -> committed.add("same tick"), () -> {}));
        assertTrue(queue.reserve(5, "first", marker -> committed.add("next tick"), () -> {}));
    }

    @Test
    void rollsBackOnlyBeforeNativeEffectStarts() {
        DeferredEffectQueue<String> queue = new DeferredEffectQueue<>();
        AtomicInteger rollbacks = new AtomicInteger();
        queue.reserve(1, "before", marker -> {
            throw new Exception("before");
        }, rollbacks::incrementAndGet);
        queue.reserve(1, "after", marker -> {
            marker.markNativeEffectStarted();
            throw new Exception("after");
        }, rollbacks::incrementAndGet);

        List<DeferredEffectQueue.Failure<String>> failures = queue.drain();

        assertEquals(2, failures.size());
        assertTrue(failures.get(0).rollbackAttempted());
        assertFalse(failures.get(1).rollbackAttempted());
        assertEquals(1, rollbacks.get());
    }

    @Test
    void reportsRollbackFailureWithoutMaskingCommitFailure() {
        DeferredEffectQueue<String> queue = new DeferredEffectQueue<>();
        queue.reserve(1, "payload", marker -> {
            throw new Exception("commit");
        }, () -> {
            throw new IllegalStateException("rollback");
        });

        DeferredEffectQueue.Failure<String> failure = queue.drain().getFirst();

        assertEquals("commit", failure.commitFailure().getMessage());
        assertEquals("rollback", failure.rollbackFailure().getMessage());
    }

    @Test
    void cancelledReservationMayBeReservedAgain() {
        DeferredEffectQueue<String> queue = new DeferredEffectQueue<>();

        assertTrue(queue.reserve(8, "payload", marker -> {}, () -> {}));
        assertTrue(queue.cancel("payload"));
        assertEquals(0, queue.pendingCount());
        assertTrue(queue.reserve(8, "payload", marker -> {}, () -> {}));
    }
}
