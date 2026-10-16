package com.volmit.bytecompanions.features.buffs.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import com.volmit.bytecompanions.features.buffs.configuration.buff.Buff;
import com.volmit.bytecompanions.features.buffs.configuration.buff.MobLoot;

@ConfigSerializable
public final class Vanilla {

    @Setting("experience")
    private Buff experience = new Buff();

    @Setting("experience-mob")
    private Buff experienceMob = new Buff();

    @Setting("experience-block")
    private Buff experienceBlock = new Buff();

    @Setting("experience-furnace")
    private Buff experienceFurnace = new Buff();

    @Setting("experience-fishing")
    private Buff experienceFishing = new Buff();

    @Setting("mob-loot")
    private MobLoot mobLoot = new MobLoot();

    @Setting("block-drops")
    private Buff blockDrops = new Buff();

    @Setting("fishing-loot")
    private Buff fishingLoot = new Buff();

    public Buff experience() {
        return this.experience;
    }

    public Buff experienceMob() {
        return this.experienceMob;
    }

    public Buff experienceBlock() {
        return this.experienceBlock;
    }

    public Buff experienceFurnace() {
        return this.experienceFurnace;
    }

    public Buff experienceFishing() {
        return this.experienceFishing;
    }

    public MobLoot mobLoot() {
        return this.mobLoot;
    }

    public Buff blockDrops() {
        return this.blockDrops;
    }

    public Buff fishingLoot() {
        return this.fishingLoot;
    }

}