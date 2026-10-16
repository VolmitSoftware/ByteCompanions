package com.volmit.bytecompanions.api.companion.animation;

public enum AnimationChannel {

    POST_TRACKING,
    TRACKING,
    MOVEMENT,
    ROTATION,
    POSE,

    FULL_BODY(true),
    HEAD_ONLY(true);

    private final boolean singleAnimation;

    AnimationChannel() {
        this(false);
    }

    AnimationChannel(final boolean singleAnimation) {
        this.singleAnimation = singleAnimation;
    }

    public boolean isSingleAnimation() {
        return this.singleAnimation;
    }

}
