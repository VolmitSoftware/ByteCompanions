package com.volmit.bytecompanions.features.particles.adapternms;

import org.bukkit.entity.Player;
import org.joml.Vector3f;
import java.util.function.Consumer;

public interface ParticleBatch {
    int size();

    void set(int index, ParticleKind kind, double x, double y, double z,
             float offsetX, float offsetY, float offsetZ, float speed, int count);

    void set(int index, DustStyle style, double x, double y, double z,
             float offsetX, float offsetY, float offsetZ, float speed, int count);

    void setDust(int index, Vector3f color, float scale, double x, double y, double z,
                 float offsetX, float offsetY, float offsetZ, float speed, int count);

    void setColored(int index, float red, float green, float blue, double x, double y, double z,
                    float offsetX, float offsetY, float offsetZ, float speed, int count);

    void copyTo(int sourceIndex, ParticleBatch target, int targetIndex, int count);

    ParticleBatch slice(int size);

    void clear();

    Consumer<Player> snapshotDelivery();

    void send(Iterable<? extends Player> viewers);
}
