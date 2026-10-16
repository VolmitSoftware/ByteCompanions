package com.volmit.bytecompanions.platform.paper.progression;

import org.bukkit.Material;
import org.spongepowered.configurate.ConfigurationNode;
import com.volmit.bytecompanions.platform.paper.configuration.ContentSnapshot;
import org.spongepowered.configurate.ConfigurationOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record ProgressionDefinitions(String serverId, boolean pausePersonalOffline,
        double maximumBoosterPercent, double maximumRewardMultiplier, long sourceCooldownMillis, Map<String, Long> xpSources,
        Set<String> allowedWorlds, List<Long> levelThresholds, Map<String, CompanionDefinition> companions,
        Map<String, BoosterDefinition> boosters) {
    public static final Set<String> CHANNELS = Set.of("companion_xp", "experience", "experience_mob",
            "experience_block", "experience_furnace", "experience_fishing", "mob_loot", "block_drops", "fishing_loot");
    public enum Scope { PERSONAL, SERVER }
    public enum Stacking { QUEUE, HIGHEST }
    public record Cost(double money, Map<String, Integer> items) {
        public Cost { items = Map.copyOf(items); }
        public boolean free() { return money == 0 && items.isEmpty(); }
    }
    public record TierDefinition(int requiredLevel, Cost cost, Map<String, Double> additions) {
        public TierDefinition { additions = Map.copyOf(additions); }
    }
    public record TrackDefinition(String name, List<TierDefinition> tiers) {
        public TrackDefinition { tiers = List.copyOf(tiers); }
    }
    public record CompanionDefinition(Map<String, TrackDefinition> tracks, Map<Integer, Map<String, Double>> levelRewards) {
        public CompanionDefinition {
            tracks = Map.copyOf(tracks);
            Map<Integer, Map<String, Double>> immutable = new LinkedHashMap<>();
            levelRewards.forEach((level, additions) -> immutable.put(level, Map.copyOf(additions)));
            levelRewards = Map.copyOf(immutable);
        }
    }
    public record BoosterDefinition(String name, Scope scope, Map<String, Double> percentages,
            long durationMillis, Stacking stacking, Cost cost) {
        public BoosterDefinition { percentages = Map.copyOf(percentages); }
    }
    public ProgressionDefinitions {
        xpSources = Map.copyOf(xpSources);
        allowedWorlds = Set.copyOf(allowedWorlds);
        levelThresholds = List.copyOf(levelThresholds);
        companions = Map.copyOf(companions);
        boosters = Map.copyOf(boosters);
    }
    public static ProgressionDefinitions defaults() {
        return new ProgressionDefinitions("local", true, 300, 10, 1000,
                Map.of("block_break", 1L, "mob_kill", 5L, "fishing", 3L),
                Set.of(), List.of(0L, 100L, 300L, 600L), Map.of(), Map.of());
    }
    public static ProgressionDefinitions parse(byte[] bytes) throws IOException {
        ConfigurationNode root = ContentSnapshot.parse(new String(bytes, StandardCharsets.UTF_8), ConfigurationOptions.defaults());
        ProgressionDefinitions base = defaults();
        fields(root, "server-id", "pause-personal-offline", "maximum-booster-percent", "maximum-reward-multiplier",
                "source-cooldown-millis", "xp-sources", "allowed-worlds", "level-thresholds", "companions", "boosters");
        String server = text(root.node("server-id"), "local");
        require(server.matches("[a-zA-Z0-9_.-]{1,48}"), "server-id must contain 1-48 letters, numbers, dots, underscores or dashes");
        double cap = number(root.node("maximum-booster-percent"), base.maximumBoosterPercent());
        double rewardCap = number(root.node("maximum-reward-multiplier"), base.maximumRewardMultiplier());
        long cooldown = integer(root.node("source-cooldown-millis"), 1000);
        require(cooldown >= 0 && cooldown <= 600000, "Invalid source-cooldown-millis");
        require(Double.isFinite(cap) && cap >= 0 && cap <= 100_000, "Invalid maximum-booster-percent");
        require(Double.isFinite(rewardCap) && rewardCap >= 1 && rewardCap <= 1_000, "Invalid maximum-reward-multiplier");
        ConfigurationNode thresholdNode = root.node("level-thresholds");
        require(thresholdNode.virtual() || thresholdNode.isList(), "level-thresholds must be a list");
        List<Long> thresholds = thresholdNode.virtual() ? base.levelThresholds()
                : thresholdNode.childrenList().stream().map(node -> integer(node, -1)).toList();
        require(!thresholds.isEmpty() && thresholds.size() <= 1000 && thresholds.getFirst() == 0, "level-thresholds must start at zero");
        for (int i = 1; i < thresholds.size(); i++) require(thresholds.get(i) > thresholds.get(i - 1), "level-thresholds must increase strictly");
        Map<String, Long> sources = new LinkedHashMap<>(base.xpSources());
        map(root.node("xp-sources"));
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("xp-sources").childrenMap().entrySet()) {
            String key = entry.getKey().toString();
            require(sources.containsKey(key), "Unknown XP source: " + key);
            long value = integer(entry.getValue(), -1);
            require(value >= 0 && value <= 1_000_000, "Invalid XP amount: " + key);
            sources.put(key, value);
        }
        Map<String, CompanionDefinition> companions = new LinkedHashMap<>();
        map(root.node("companions"));
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("companions").childrenMap().entrySet()) {
            String id = id(entry.getKey());
            fields(entry.getValue(), "tracks", "level-rewards");
            Map<String, TrackDefinition> tracks = new LinkedHashMap<>();
            map(entry.getValue().node("tracks"));
            for (Map.Entry<Object, ? extends ConfigurationNode> track : entry.getValue().node("tracks").childrenMap().entrySet()) {
                fields(track.getValue(), "name", "tiers");
                require(track.getValue().node("tiers").isList(), "tiers must be a list");
                List<TierDefinition> tiers = new ArrayList<>();
                for (ConfigurationNode tier : track.getValue().node("tiers").childrenList()) {
                    fields(tier, "required-level", "cost", "additions");
                    int level = Math.toIntExact(integer(tier.node("required-level"), 1));
                    require(level >= 1 && level <= thresholds.size(), "Invalid required-level for " + id);
                    tiers.add(new TierDefinition(level, cost(tier.node("cost")), additions(tier.node("additions"))));
                }
                require(!tiers.isEmpty() && tiers.size() <= 100, "Each track needs 1-100 tiers");
                tracks.put(id(track.getKey()), new TrackDefinition(text(track.getValue().node("name"), track.getKey().toString()), tiers));
            }
            Map<Integer, Map<String, Double>> rewards = new LinkedHashMap<>();
            map(entry.getValue().node("level-rewards"));
            for (Map.Entry<Object, ? extends ConfigurationNode> reward : entry.getValue().node("level-rewards").childrenMap().entrySet()) {
                int level = Integer.parseInt(reward.getKey().toString());
                require(level >= 1 && level <= thresholds.size(), "Invalid level reward for " + id);
                rewards.put(level, additions(reward.getValue()));
            }
            companions.put(id, new CompanionDefinition(tracks, rewards));
        }
        Map<String, BoosterDefinition> boosters = new LinkedHashMap<>();
        map(root.node("boosters"));
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.node("boosters").childrenMap().entrySet()) {
            String id = id(entry.getKey());
            ConfigurationNode node = entry.getValue();
            fields(node, "name", "duration-seconds", "percentages", "scope", "stacking", "cost");
            long seconds = integer(node.node("duration-seconds"), 0);
            require(seconds > 0 && seconds <= 31_536_000, "Booster duration must be 1 second through 1 year");
            Map<String, Double> channels = new LinkedHashMap<>();
            map(node.node("percentages"));
            for (Map.Entry<Object, ? extends ConfigurationNode> channel : node.node("percentages").childrenMap().entrySet()) {
                String name = channel.getKey().toString();
                double percent = number(channel.getValue(), Double.NaN);
                require(CHANNELS.contains(name) && Double.isFinite(percent) && percent > 0 && percent <= cap, "Invalid booster percentage: " + name);
                channels.put(name, percent);
            }
            require(!channels.isEmpty(), "Booster must have a percentage channel: " + id);
            boosters.put(id, new BoosterDefinition(text(node.node("name"), id),
                    Scope.valueOf(text(node.node("scope"), "PERSONAL").toUpperCase(Locale.ROOT)),
                    channels, seconds * 1000,
                    Stacking.valueOf(text(node.node("stacking"), "QUEUE").toUpperCase(Locale.ROOT)), cost(node.node("cost"))));
        }
        ConfigurationNode worlds = root.node("allowed-worlds");
        require(worlds.virtual() || worlds.isList(), "allowed-worlds must be a list");
        return new ProgressionDefinitions(server, bool(root.node("pause-personal-offline"), true), cap, rewardCap, cooldown,
                sources, Set.copyOf(worlds.childrenList().stream().map(world -> text(world, "")).toList()), thresholds, companions, boosters);
    }
    public void validateCompanions(Set<String> ids) {
        for (String id : companions.keySet()) require(ids.contains(id), "Unknown progression companion: " + id);
    }
    private static Cost cost(ConfigurationNode node) {
        fields(node, "money", "items");
        double money = number(node.node("money"), 0);
        require(Double.isFinite(money) && money >= 0 && money <= 1_000_000_000, "Invalid purchase money amount");
        Map<String, Integer> items = new LinkedHashMap<>();
        map(node.node("items"));
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.node("items").childrenMap().entrySet()) {
            Material material = Material.matchMaterial(entry.getKey().toString());
            int count = Math.toIntExact(integer(entry.getValue(), 0));
            require(material != null && material.isItem() && !material.isAir() && count > 0 && count <= 2304, "Invalid purchase item cost: " + entry.getKey());
            require(items.put(material.name(), count) == null, "Duplicate purchase material: " + material);
        }
        return new Cost(money, items);
    }
    private static Map<String, Double> additions(ConfigurationNode node) {
        map(node);
        Map<String, Double> additions = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            String key = entry.getKey().toString();
            double value = number(entry.getValue(), Double.NaN);
            require(key.matches("(?:buff:[a-z_]+|(?:attribute|effect):[a-z0-9_.-]+:[a-z0-9_/.-]+)"), "Invalid bonus target: " + key);
            require(Double.isFinite(value) && Math.abs(value) <= 1_000_000, "Invalid bonus amount: " + key);
            if (key.startsWith("buff:")) require(CHANNELS.contains(key.substring(5)) && !key.equals("buff:companion_xp"), "Invalid buff target: " + key);
            if (key.startsWith("effect:")) require(value == Math.rint(value) && value >= 0 && value <= 255, "Potion additions must be integers from 0 through 255");
            additions.put(key, value);
        }
        require(!additions.isEmpty(), "An upgrade or level reward needs at least one addition");
        return additions;
    }
    private static void map(ConfigurationNode node) {
        require(node.virtual() || node.isMap(), node.path() + " must be a map");
    }
    private static void fields(ConfigurationNode node, String... fields) {
        map(node);
        Set<String> allowed = Set.of(fields);
        for (Object key : node.childrenMap().keySet()) require(key instanceof String && allowed.contains(key), "Unknown field " + node.path() + "." + key);
    }
    private static String text(ConfigurationNode node, String fallback) {
        if (node.virtual()) return fallback;
        require(node.raw() instanceof String, node.path() + " must be text");
        String value = (String) node.raw();
        require(!value.isBlank() && value.length() <= 256, node.path() + " must contain 1-256 characters");
        return value;
    }
    private static double number(ConfigurationNode node, double fallback) {
        if (node.virtual()) return fallback;
        require(node.raw() instanceof Number, node.path() + " must be numeric");
        double value = ((Number) node.raw()).doubleValue();
        require(Double.isFinite(value), node.path() + " must be finite");
        return value;
    }
    private static long integer(ConfigurationNode node, long fallback) {
        if (node.virtual()) return fallback;
        require(node.raw() instanceof Byte || node.raw() instanceof Short || node.raw() instanceof Integer || node.raw() instanceof Long,
                node.path() + " must be an integer");
        return ((Number) node.raw()).longValue();
    }
    private static boolean bool(ConfigurationNode node, boolean fallback) {
        if (node.virtual()) return fallback;
        require(node.raw() instanceof Boolean, node.path() + " must be true or false");
        return (Boolean) node.raw();
    }
    private static String id(Object raw) {
        String id = raw.toString();
        require(id.matches("[a-z0-9_.-]{1,64}"), "Invalid definition ID: " + id);
        return id;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
