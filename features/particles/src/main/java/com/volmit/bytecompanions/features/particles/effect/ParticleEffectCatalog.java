package com.volmit.bytecompanions.features.particles.effect;

import com.volmit.bytecompanions.features.particles.effect.types.AngelRingParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.AuraParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.BatmanParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.CloudParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.DoubleHelixParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.DragonWingParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.LoveParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.MagicAuraParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.MagicParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.MusicParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.OrbParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.PendulumParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.PulseParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.RainbowParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.RainbowUmbrellaParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.RotatingRingParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.ScannerParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.UmbrellaParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.HaloSparksParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.EmberTrailParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.SnowfallParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.CherryBlossomParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.LightningCrackleParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.SoulFlameSpiralParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.EmeraldCharmParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.StarCrownParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.JackOLanternParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.HauntedWispsParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.AutumnLeavesParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.HarvestGlowParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.ChristmasLightsParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.CandyCaneSwirlParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.FireworksBurstParticleEffect;
import com.volmit.bytecompanions.features.particles.effect.types.MidnightConfettiParticleEffect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ParticleEffectCatalog {

    private static final Map<String, Supplier<ParticleEffect>> FACTORIES = createFactories();

    private ParticleEffectCatalog() {
    }

    public static Map<String, Supplier<ParticleEffect>> factories() {
        return FACTORIES;
    }

    public static boolean contains(String id) {
        return FACTORIES.containsKey(id);
    }

    private static Map<String, Supplier<ParticleEffect>> createFactories() {
        Map<String, Supplier<ParticleEffect>> factories = new LinkedHashMap<>();
        factories.put("angel_ring_effect", AngelRingParticleEffect::new);
        factories.put("aura_effect", AuraParticleEffect::new);
        factories.put("batman_effect", BatmanParticleEffect::new);
        factories.put("cloud_effect", CloudParticleEffect::new);
        factories.put("double_helix_effect", DoubleHelixParticleEffect::new);
        factories.put("dragon_wing_effect", DragonWingParticleEffect::new);
        factories.put("love_particle_effect", LoveParticleEffect::new);
        factories.put("magic_aura_effect", MagicAuraParticleEffect::new);
        factories.put("magic_effect", MagicParticleEffect::new);
        factories.put("music_effect", MusicParticleEffect::new);
        factories.put("orb_effect", OrbParticleEffect::new);
        factories.put("pendulum_effect", PendulumParticleEffect::new);
        factories.put("pulse_effect", PulseParticleEffect::new);
        factories.put("rainbow_effect", RainbowParticleEffect::new);
        factories.put("rainbow_umbrella_effect", RainbowUmbrellaParticleEffect::new);
        factories.put("rotating_ring_effect", RotatingRingParticleEffect::new);
        factories.put("scanner_effect", ScannerParticleEffect::new);
        factories.put("umbrella_effect", UmbrellaParticleEffect::new);
        factories.put("halo_sparks_effect", HaloSparksParticleEffect::new);
        factories.put("ember_trail_effect", EmberTrailParticleEffect::new);
        factories.put("snowfall_effect", SnowfallParticleEffect::new);
        factories.put("cherry_blossom_effect", CherryBlossomParticleEffect::new);
        factories.put("lightning_crackle_effect", LightningCrackleParticleEffect::new);
        factories.put("soul_flame_spiral_effect", SoulFlameSpiralParticleEffect::new);
        factories.put("emerald_charm_effect", EmeraldCharmParticleEffect::new);
        factories.put("star_crown_effect", StarCrownParticleEffect::new);
        factories.put("jack_o_lantern_effect", JackOLanternParticleEffect::new);
        factories.put("haunted_wisps_effect", HauntedWispsParticleEffect::new);
        factories.put("autumn_leaves_effect", AutumnLeavesParticleEffect::new);
        factories.put("harvest_glow_effect", HarvestGlowParticleEffect::new);
        factories.put("christmas_lights_effect", ChristmasLightsParticleEffect::new);
        factories.put("candy_cane_swirl_effect", CandyCaneSwirlParticleEffect::new);
        factories.put("fireworks_burst_effect", FireworksBurstParticleEffect::new);
        factories.put("midnight_confetti_effect", MidnightConfettiParticleEffect::new);
        return Collections.unmodifiableMap(factories);
    }
}
