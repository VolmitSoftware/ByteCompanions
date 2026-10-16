package com.volmit.bytecompanions.api.companion.animation;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;
import com.volmit.bytecompanions.api.companion.anchor.CompanionAnchor;

import java.util.Collection;

/**
 * Snapshot of the companion's state for a single animation tick.
 *
 * <p>Passed to every {@link com.volmit.bytecompanions.api.companion.animation.types.Animation#tick(AnimationContext)} call. All positional values
 * are in world-space. "Current" refers to the position at the start of this tick;
 * "previous" refers to the position at the start of the previous tick.
 *
 * <p>{@link #deltaTime()} accounts for variable tick intervals (e.g. every 3 server ticks),
 * so time-based animations should always scale their increments by it.
 */
public interface AnimationContext {

    /** Players for whom this companion is currently loaded and rendered by the client. **/
    @NotNull
    @UnmodifiableView
    Collection<Player> viewers();

    /**
     * The anchor this companion is attached to (normally the owner player).
     * Use this to read the anchor's current position, rotation, and world.
     */
    @NotNull CompanionAnchor anchor();

    /** X coordinate of the anchor on the previous tick. */
    double previousAnchorX();

    /** Y coordinate of the anchor on the previous tick. */
    double previousAnchorY();

    /** Z coordinate of the anchor on the previous tick. */
    double previousAnchorZ();

    /** Current X coordinate of the companion entity. */
    double currentX();

    /** Current Y coordinate of the companion entity. */
    double currentY();

    /** Current Z coordinate of the companion entity. */
    double currentZ();

    /** X coordinate of the companion entity on the previous tick. */
    double previousX();

    /** Y coordinate of the companion entity on the previous tick. */
    double previousY();

    /** Z coordinate of the companion entity on the previous tick. */
    double previousZ();

    /** Current body yaw of the companion, in degrees. */
    float bodyYaw();

    /** Current head yaw of the companion, in degrees. */
    float headYaw();

    /** Current head pitch of the companion, in degrees. */
    float headPitch();

    /**
     * Elapsed time in seconds since the previous animation tick.
     * <p>
     * may be called every 3 or more server ticks, this value is not a fixed constant.
     * Multiply time-based increments by this value to keep animations TPS-independent.
     *
     * <p>Example: {@code this.time += speed * ctx.deltaTime();}
     */
    double deltaTime();

    /**
     * Total elapsed time in seconds since the companion engine started ticking.
     *
     * <p>Unlike {@link #deltaTime()}, which is relative and per-companion (it starts
     * counting from when that specific companion began ticking), this value is a
     * single shared clock read once per engine cycle and passed identically to every
     * companion evaluated in that cycle — regardless of when each companion was created.
     *
     * <p>Use this instead of a locally accumulated timer or {@code System.nanoTime()}
     * whenever an animation needs a phase/angle that must stay synchronized across
     * multiple companions of the same owner (e.g. orbit rotation), so that companions
     * spawned at different times don't drift out of alignment with each other.
     *
     * <p>Example: {@code final double angle = baseAngle + ctx.elapsedTime() * angularSpeed;}
     */
    double elapsedTime();

    /**
     * The contiguous position of this companion among the owner's currently
     * active companions, in the range {@code [0, formatCount())}.
     *
     * <p>Recalculated live whenever the owner's active companion set changes
     * (equip/unequip), so this value may change between ticks — formations
     * should read it fresh on every call rather than caching it.
     *
     * <p>Not to be confused with a permanent per-companion identifier: this index
     * is purely positional and only meaningful relative to {@link #formatCount()}.
     */
    int formatIndex();

    /**
     * The total number of companions currently active for this companion's owner.
     *
     * <p>Used together with {@link #formatIndex()} by {@code Formation} implementations
     * to distribute companions evenly (e.g. dividing a circle or grid), and recalculated
     * live on every equip/unequip — never assume it stays constant across ticks.
     */
    int formatCount();

}