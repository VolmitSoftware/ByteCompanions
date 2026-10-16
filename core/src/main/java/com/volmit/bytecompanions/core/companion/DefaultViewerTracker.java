package com.volmit.bytecompanions.core.companion;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;
import com.volmit.bytecompanions.spi.companions.ViewerTracker;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe viewer tracker backed by a {@link ConcurrentHashMap} key-set.
 *
 * <p>The mutable {@link #viewers()} set is exposed to NMS adapters for
 * iterator-based removal during tick updates. The public API
 * ({@link #all()}) always returns an immutable snapshot.
 */
public final class DefaultViewerTracker implements ViewerTracker {

    private final Set<Player> viewers = ConcurrentHashMap.newKeySet();

    /** Mutable view — used by adapters for iterator-based removal. */
    public @NotNull Set<Player> viewers() {
        return this.viewers;
    }

    @Contract(pure = true)
    public @NotNull @UnmodifiableView Collection<Player> unmodifiableViewers() {
        return Collections.unmodifiableCollection(this.viewers);
    }

    public boolean add(final @NotNull Player player) {
        return this.viewers.add(player);
    }

    public boolean remove(final @NotNull Player player) {
        return this.viewers.remove(player);
    }

    public boolean isViewer(final @NotNull Player player) {
        return this.viewers.contains(player);
    }

    public void clear() {
        this.viewers.clear();
    }

    public boolean isEmpty() {
        return this.viewers.isEmpty();
    }

    public int size() {
        return this.viewers.size();
    }

    @Contract(pure = true)
    @Override
    public @NotNull @Unmodifiable Collection<Player> all() {
        return List.copyOf(this.viewers);
    }

}
