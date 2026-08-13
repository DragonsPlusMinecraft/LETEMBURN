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

package dev.marblegate.letemburn.common.effect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DeferredEffectQueue<K> {
    private final Map<K, PendingEffect> pending = new LinkedHashMap<>();
    private final Map<K, Long> seenEpochs = new LinkedHashMap<>();

    public synchronized boolean reserve(
            long epoch, K key, TransactionalEffect effect, Runnable rollback) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(rollback, "rollback");
        discardSeenBefore(epoch);
        if (pending.containsKey(key) || seenEpochs.containsKey(key)) {
            return false;
        }
        pending.put(key, new PendingEffect(effect, rollback));
        seenEpochs.put(key, epoch);
        return true;
    }

    public synchronized List<Failure<K>> drain() {
        List<Map.Entry<K, PendingEffect>> snapshot = new ArrayList<>(pending.entrySet());
        pending.clear();
        List<Failure<K>> failures = new ArrayList<>();
        for (Map.Entry<K, PendingEffect> entry : snapshot) {
            TransactionalEffect.CommitMarker marker = new TransactionalEffect.CommitMarker();
            try {
                entry.getValue().effect().commit(marker);
            } catch (Exception commitFailure) {
                Exception rollbackFailure = null;
                boolean rollbackAttempted = !marker.nativeEffectStarted();
                if (rollbackAttempted) {
                    try {
                        entry.getValue().rollback().run();
                    } catch (RuntimeException failure) {
                        rollbackFailure = failure;
                    }
                }
                failures.add(new Failure<>(entry.getKey(), commitFailure, rollbackAttempted, rollbackFailure));
            }
        }
        return List.copyOf(failures);
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    synchronized void beginPhysicsTick() {
        seenEpochs.keySet().removeIf(key -> !pending.containsKey(key));
    }

    public synchronized boolean cancel(K key) {
        PendingEffect removed = pending.remove(key);
        if (removed != null) {
            seenEpochs.remove(key);
            return true;
        }
        return false;
    }

    private void discardSeenBefore(long epoch) {
        Iterator<Map.Entry<K, Long>> iterator = seenEpochs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, Long> entry = iterator.next();
            if (entry.getValue() < epoch && !pending.containsKey(entry.getKey())) {
                iterator.remove();
            }
        }
    }

    private record PendingEffect(TransactionalEffect effect, Runnable rollback) {}

    public record Failure<K>(
            K key, Exception commitFailure, boolean rollbackAttempted, Exception rollbackFailure) {}
}
