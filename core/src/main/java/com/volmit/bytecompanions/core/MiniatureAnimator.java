package com.volmit.bytecompanions.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.volmit.bytecompanions.api.companion.animation.types.Animation;
import com.volmit.bytecompanions.api.companion.animation.AnimationChannel;
import com.volmit.bytecompanions.api.companion.animation.AnimationContext;
import com.volmit.bytecompanions.api.companion.animation.output.FullBodyOutput;
import com.volmit.bytecompanions.api.companion.animation.output.HeadOnlyOutput;
import com.volmit.bytecompanions.api.companion.animation.output.MovementOutput;
import com.volmit.bytecompanions.api.companion.animation.output.PoseOutput;
import com.volmit.bytecompanions.api.companion.animation.output.RotationOutput;
import com.volmit.bytecompanions.core.companion.AnimationFrame;
import com.volmit.bytecompanions.core.companion.AnimationsSet;

import java.util.Map;

/**
 * Frame-based animation pipeline for a single companion entity.
 *
 * <p>Each tick, the animator evaluates all registered animations and produces
 * a single {@link AnimationFrame} that the NMS adapter applies to the armor stand.
 *
 * <h3>Evaluation order</h3>
 * <ol>
 *   <li><em>Tracking</em> animations always run first, unconditionally — they handle follow/pursuit
 *       logic and must execute regardless of what other channels are active.</li>
 *   <li>If a <em>full-body</em> animation exists it runs exclusively — all other channels are skipped.</li>
 *   <li>Otherwise the individual channels run in parallel:
 *       <em>movement</em> (additive deltas), <em>rotation</em> (last-non-null wins),
 *       <em>pose</em> (per-axis blend), and <em>head-only</em> (overrides rotation head values).</li>
 * </ol>
 */
public final class MiniatureAnimator {

    private final AnimationsSet animationsSet = new AnimationsSet();

    /** Returns the mutable animation set used to register/remove animations. */
    public @NotNull AnimationsSet animationSet() {
        return this.animationsSet;
    }

    /** Clears all animations from every channel. */
    public void stopAll() {
        this.animationsSet.clear();
    }

    /**
     * Evaluates all active animations and produces the frame delta for this tick.
     *
     * @param animationContext per-tick spatial snapshot
     * @return the composed frame to apply to the armor stand
     */
    public @NotNull AnimationFrame tick(final @NotNull AnimationContext animationContext) {
        // Keep AnimationsSet context up-to-date so onStart/onStop lifecycle hooks work
        this.animationsSet.updateContext(animationContext);

        // TRACKING always runs first — it is channel-independent and must execute
        // even when a FULL_BODY animation is active.
        this.runTracking(animationContext);

        final FullBodyOutput fullBodyOutput = this.runFullBody(animationContext);
        if (fullBodyOutput != null) {
            return this.buildFrameFromFullBody(fullBodyOutput);
        }

        final MovementOutput movementOutput = this.runMovement(animationContext);
        final RotationOutput rotationOutput = this.runRotation(animationContext);
        final PoseOutput poseOutput = this.runPose(animationContext);
        final HeadOnlyOutput headOnlyOutput = this.runHeadOnly(animationContext);

        return this.buildFrameFromChannels(
                movementOutput,
                rotationOutput,
                poseOutput,
                headOnlyOutput
        );
    }

    public void runPostTracking(final @NotNull AnimationContext animationContext) {
        for (final Animation<?> trackingAnimation : this.animationsSet.getAnimations(AnimationChannel.POST_TRACKING)) {
            if (!trackingAnimation.isActive()) {
                continue;
            }

            trackingAnimation.tick(animationContext);
        }
    }

    private @Nullable FullBodyOutput runFullBody(final @NotNull AnimationContext animationContext) {
        final Map<String, ? extends Animation<?>> animations = this.animationsSet.getFastMapLookup(AnimationChannel.FULL_BODY);
        if (animations.isEmpty()) {
            return null;
        }

        Animation<?> animation = animations.values().iterator().next();
        return animation.isActive() ? (FullBodyOutput) animation.tick(animationContext) : null;
    }

    private @NotNull MovementOutput runMovement(final @NotNull AnimationContext animationContext) {
        final MovementOutput movementOutput = new MovementOutput();
        for (final Animation<?> movementAnimation : this.animationsSet.getFastCollectionLookup(AnimationChannel.MOVEMENT)) {
            if (!movementAnimation.isActive()) {
                continue;
            }

            final MovementOutput singleOutput = (MovementOutput) movementAnimation.tick(animationContext);
            movementOutput.deltaX += singleOutput.deltaX;
            movementOutput.deltaY += singleOutput.deltaY;
            movementOutput.deltaZ += singleOutput.deltaZ;
        }

        return movementOutput;
    }

    private @NotNull RotationOutput runRotation(final @NotNull AnimationContext animationContext) {
        final RotationOutput rotationOutput = new RotationOutput();
        for (final Animation<?> rotationAnimation : this.animationsSet.getFastCollectionLookup(AnimationChannel.ROTATION)) {
            if (!rotationAnimation.isActive()) {
                continue;
            }

            final RotationOutput singleOutput = (RotationOutput) rotationAnimation.tick(animationContext);
            if (singleOutput.bodyYaw != null) {
                rotationOutput.bodyYaw = singleOutput.bodyYaw;
            }

            if (singleOutput.headPitch != null) {
                rotationOutput.headPitch = singleOutput.headPitch;
            }
        }

        return rotationOutput;
    }

    private @NotNull PoseOutput runPose(final @NotNull AnimationContext animationContext) {
        final PoseOutput poseOutput = new PoseOutput();
        for (final Animation<?> poseAnimation : this.animationsSet.getFastCollectionLookup(AnimationChannel.POSE)) {
            if (!poseAnimation.isActive()) {
                continue;
            }

            final PoseOutput singleOutput = (PoseOutput) poseAnimation.tick(animationContext);
            this.blendPose(poseOutput, singleOutput);
        }

        return poseOutput;
    }

    private @Nullable HeadOnlyOutput runHeadOnly(final @NotNull AnimationContext animationContext) {
        final Map<String, ? extends Animation<?>> animations = this.animationsSet.getFastMapLookup(AnimationChannel.HEAD_ONLY);
        if (animations.isEmpty()) {
            return null;
        }

        Animation<?> animation = animations.values().iterator().next();
        return animation.isActive() ? (HeadOnlyOutput) animation.tick(animationContext) : null;
    }

    private void runTracking(final @NotNull AnimationContext animationContext) {
        for (final Animation<?> trackingAnimation : this.animationsSet.getFastCollectionLookup(AnimationChannel.TRACKING)) {
            if (!trackingAnimation.isActive()) {
                continue;
            }

            trackingAnimation.tick(animationContext);
        }
    }

    /**
     * Blends {@code newPose} into {@code basePose} using last-non-null-wins per axis.
     */
    private void blendPose(final @NotNull PoseOutput basePose, final @NotNull PoseOutput newPose) {
        if (newPose.leftArmX != null) {
            basePose.leftArmX = newPose.leftArmX;
        }

        if (newPose.leftArmY != null) {
            basePose.leftArmY = newPose.leftArmY;
        }

        if (newPose.leftArmZ != null) {
            basePose.leftArmZ = newPose.leftArmZ;
        }

        if (newPose.rightArmX != null) {
            basePose.rightArmX = newPose.rightArmX;
        }

        if (newPose.rightArmY != null) {
            basePose.rightArmY = newPose.rightArmY;
        }

        if (newPose.rightArmZ != null) {
            basePose.rightArmZ = newPose.rightArmZ;
        }

        if (newPose.leftLegX != null) {
            basePose.leftLegX = newPose.leftLegX;
        }

        if (newPose.leftLegY != null) {
            basePose.leftLegY = newPose.leftLegY;
        }

        if (newPose.leftLegZ != null) {
            basePose.leftLegZ = newPose.leftLegZ;
        }

        if (newPose.rightLegX != null) {
            basePose.rightLegX = newPose.rightLegX;
        }

        if (newPose.rightLegY != null) {
            basePose.rightLegY = newPose.rightLegY;
        }

        if (newPose.rightLegZ != null) {
            basePose.rightLegZ = newPose.rightLegZ;
        }
    }

    private @NotNull AnimationFrame buildFrameFromFullBody(final @NotNull FullBodyOutput fullBodyOutput) {
        final AnimationFrame animationFrame = AnimationFrame.empty();

        animationFrame.absoluteX = fullBodyOutput.x;
        animationFrame.absoluteY = fullBodyOutput.y;
        animationFrame.absoluteZ = fullBodyOutput.z;
        animationFrame.absoluteBodyYaw = fullBodyOutput.yaw;
        animationFrame.absoluteHeadPitch = fullBodyOutput.pitch;

        if (fullBodyOutput.pose != null) {
            animationFrame.leftArm = fullBodyOutput.pose.leftArmRotations();
            animationFrame.rightArm = fullBodyOutput.pose.rightArmRotations();
            animationFrame.leftLeg = fullBodyOutput.pose.leftLegRotations();
            animationFrame.rightLeg = fullBodyOutput.pose.rightLegRotations();
        }

        return animationFrame;
    }

    private @NotNull AnimationFrame buildFrameFromChannels(
            final @NotNull MovementOutput movementOutput,
            final @NotNull RotationOutput rotationOutput,
            final @NotNull PoseOutput poseOutput,
            final HeadOnlyOutput headOnlyOutput
    ) {
        final AnimationFrame animationFrame = AnimationFrame.empty();

        animationFrame.deltaX = movementOutput.deltaX;
        animationFrame.deltaY = movementOutput.deltaY;
        animationFrame.deltaZ = movementOutput.deltaZ;

        if (rotationOutput.bodyYaw != null) {
            animationFrame.absoluteBodyYaw = rotationOutput.bodyYaw;
        }

        if (rotationOutput.headPitch != null) {
            animationFrame.absoluteHeadPitch = rotationOutput.headPitch;
        }

        if (headOnlyOutput != null) {
            if (headOnlyOutput.headYaw != null) {
                animationFrame.absoluteBodyYaw = headOnlyOutput.headYaw;
            }

            if (headOnlyOutput.headPitch != null) {
                animationFrame.absoluteHeadPitch = headOnlyOutput.headPitch;
            }
        }

        animationFrame.leftArm = poseOutput.leftArmRotations();
        animationFrame.rightArm = poseOutput.rightArmRotations();
        animationFrame.leftLeg = poseOutput.leftLegRotations();
        animationFrame.rightLeg = poseOutput.rightLegRotations();

        return animationFrame;
    }


    public AnimationsSet miniatureAnimationsSet() {
        return this.animationsSet;
    }
}
