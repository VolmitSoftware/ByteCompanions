package com.volmit.bytecompanions.core.companion;

import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.MovementOutput;
import com.volmit.bytecompanions.api.companion.animation.types.MovementAnimation;
import com.volmit.bytecompanions.api.companion.behavior.CompanionAction;
import com.volmit.bytecompanions.api.companion.behavior.CompanionActionResult;
import com.volmit.bytecompanions.api.companion.behavior.CompanionBehavior;
import com.volmit.bytecompanions.api.companion.behavior.CompanionBehaviorPolicy;
import com.volmit.bytecompanions.api.companion.behavior.CompanionMode;
import com.volmit.bytecompanions.api.util.Rotations;

import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class CompanionBehaviorController implements CompanionBehavior {
    private final CompanionModel model;
    private final Supplier<Position> position;
    private final LongSupplier clock;
    private CompanionBehaviorPolicy policy = CompanionBehaviorPolicy.disabled();
    private CompanionMode mode = CompanionMode.FOLLOW;
    private MovementAnimation formation;
    private AnimationContext lastContext;
    private Position held;
    private CompanionAction emote;
    private long emoteStarted;
    private long lastAction;
    private boolean acted;
    private long lastIdle;
    private boolean idleInitialized;
    private boolean recall;
    private boolean restorePose;
    private boolean closed;

    public CompanionBehaviorController(CompanionModel model, Supplier<Position> position, LongSupplier clock) {
        this.model = Objects.requireNonNull(model, "model");
        this.position = Objects.requireNonNull(position, "position");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public record Position(UUID world, double x, double y, double z, float yaw) {
        public Position {
            Objects.requireNonNull(world, "world");
        }
    }

    @Override
    public CompanionActionResult act(CompanionAction action) {
        Objects.requireNonNull(action, "action");
        if (closed || !policy.enabled()) {
            return CompanionActionResult.DISABLED;
        }
        long now = clock.getAsLong();
        if (action != CompanionAction.CALL && acted && now - lastAction < policy.actionCooldown().toNanos()) {
            return CompanionActionResult.COOLDOWN;
        }
        lastAction = now;
        acted = true;
        lastIdle = now;
        idleInitialized = true;
        switch (action) {
            case CALL -> {
                reset();
                recall = true;
            }
            case SIT, STAY -> {
                held = position.get();
                mode = action == CompanionAction.SIT ? CompanionMode.SIT : CompanionMode.STAY;
                if (emote != null || mode == CompanionMode.STAY) restorePose = true;
                emote = null;
            }
            case PET, WAVE -> {
                if (emote != null) restorePose = true;
                emote = action;
                emoteStarted = now;
            }
        }
        return CompanionActionResult.APPLIED;
    }

    @Override
    public CompanionMode mode() {
        return mode;
    }

    @Override
    public void policy(CompanionBehaviorPolicy policy) {
        if (closed) throw new IllegalStateException("Companion behavior is closed");
        this.policy = Objects.requireNonNull(policy, "policy");
        if (!policy.enabled()) reset();
        lastIdle = clock.getAsLong();
        idleInitialized = true;
    }

    @Override
    public void formation(MovementAnimation animation) {
        if (closed) throw new IllegalStateException("Companion behavior is closed");
        if (formation != null && lastContext != null) formation.onStop(lastContext);
        formation = animation;
        if (formation != null && lastContext != null) formation.onStart(lastContext);
    }

    @Override
    public void resetPose() {
        if (closed) throw new IllegalStateException("Companion behavior is closed");
        restorePose = true;
    }

    @Override
    public void reset() {
        if (mode == CompanionMode.SIT || emote != null) restorePose = true;
        mode = CompanionMode.FOLLOW;
        held = null;
        emote = null;
        recall = false;
        lastIdle = clock.getAsLong();
        idleInitialized = true;
    }

    public void close() {
        if (closed) return;
        reset();
        closed = true;
        MovementAnimation removed = formation;
        AnimationContext context = lastContext;
        formation = null;
        lastContext = null;
        if (removed != null && context != null) removed.onStop(context);
    }

    public void apply(AnimationContext context, AnimationFrame frame) {
        if (closed) return;
        if (lastContext == null && formation != null) formation.onStart(context);
        lastContext = context;
        long now = clock.getAsLong();
        if (formation != null && formation.isActive()) {
            MovementOutput movement = formation.tick(context);
            frame.absoluteX = null;
            frame.absoluteZ = null;
            frame.deltaX = movement.deltaX;
            frame.deltaZ = movement.deltaZ;
        }
        if (held != null) {
            double dx = context.anchor().x() - held.x();
            double dy = context.anchor().y() - held.y();
            double dz = context.anchor().z() - held.z();
            if (!held.world().equals(context.anchor().world().getUID())
                    || dx * dx + dy * dy + dz * dz > policy.maximumHoldDistance() * policy.maximumHoldDistance()) {
                reset();
                recall = true;
            }
        }
        if (recall) {
            double angle = Math.toRadians(context.anchor().yaw());
            frame.absoluteX = context.anchor().x() - Math.sin(angle) * 1.5;
            frame.absoluteY = context.anchor().y() + 0.3;
            frame.absoluteZ = context.anchor().z() + Math.cos(angle) * 1.5;
            frame.deltaX = frame.deltaY = frame.deltaZ = 0;
            recall = false;
        }
        if (held != null) {
            frame.absoluteX = held.x();
            frame.absoluteY = held.y();
            frame.absoluteZ = held.z();
            frame.deltaX = frame.deltaY = frame.deltaZ = 0;
            frame.absoluteBodyYaw = held.yaw();
            frame.deltaBodyYaw = 0;
        }
        if (emote != null && now - emoteStarted >= policy.emoteDuration().toNanos()) {
            emote = null;
            restorePose = true;
        }
        if (restorePose) {
            neutralMissingPose(frame);
            restorePose = false;
        }
        if (mode == CompanionMode.SIT) {
            frame.absoluteHeadPitch = 10.0f;
            frame.deltaHeadPitch = 0;
            if (model == CompanionModel.FULL_BODY) {
                frame.leftLeg = new Rotations(-90, 0, -8);
                frame.rightLeg = new Rotations(-90, 0, 8);
                frame.leftArm = new Rotations(-10, 0, -10);
                frame.rightArm = new Rotations(-10, 0, 10);
            }
        }
        if (!idleInitialized) {
            lastIdle = now;
            idleInitialized = true;
        }
        double movementX = context.anchor().x() - context.previousAnchorX();
        double movementZ = context.anchor().z() - context.previousAnchorZ();
        if (movementX * movementX + movementZ * movementZ > 0.0001) lastIdle = now;
        if (policy.enabled() && policy.idleReactions() && emote == null
                && now - lastIdle >= policy.idleInterval().toNanos()) {
            emote = CompanionAction.PET;
            emoteStarted = now;
            lastIdle = now;
        }
        if (emote != null) {
            double progress = Math.max(0, Math.min(1, (double) (now - emoteStarted) / policy.emoteDuration().toNanos()));
            float nod = (float) (Math.sin(progress * Math.PI * 4) * Math.sin(progress * Math.PI) * 18);
            frame.absoluteHeadPitch = nod;
            frame.deltaHeadPitch = 0;
            if (emote == CompanionAction.WAVE && model == CompanionModel.FULL_BODY) {
                frame.rightArm = new Rotations(-150, 0, (float) (25 + Math.sin(progress * Math.PI * 8) * 20));
            }
        }
    }

    private void neutralMissingPose(AnimationFrame frame) {
        if (frame.absoluteHeadPitch == null) frame.absoluteHeadPitch = 0.0f;
        if (model == CompanionModel.FULL_BODY) {
            if (frame.leftArm == null) frame.leftArm = new Rotations(0, 0, 0);
            if (frame.rightArm == null) frame.rightArm = new Rotations(0, 0, 0);
            if (frame.leftLeg == null) frame.leftLeg = new Rotations(0, 0, 0);
            if (frame.rightLeg == null) frame.rightLeg = new Rotations(0, 0, 0);
        }
    }
}
