package com.volmit.bytecompanions.platform.paper.configuration;

import art.arcane.volmlib.util.config.ConfigDescription;
import art.arcane.volmlib.util.config.ConfigDoc;
import com.volmit.bytecompanions.api.ConfigurationSettings;
import com.volmit.bytecompanions.api.companion.CompanionModel;
import com.volmit.bytecompanions.platform.paper.collection.ViewerPreferences;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.List;

@ConfigSerializable
@ConfigDescription("Root settings for ByteCompanions. Companion definitions and feature packs keep their own TOML files beside this one.")
public final class Configuration implements ConfigurationSettings {
    private GeneralConfig general = new GeneralConfig();
    private MetricsConfig metrics = new MetricsConfig();
    private PresentationConfig presentation = new PresentationConfig();
    private HotReloadConfig hotReload = new HotReloadConfig();
    private DebugConfig debug = new DebugConfig();
    private Storage storage = new Storage();
    private PerformanceConfig performance = new PerformanceConfig();
    private ServerRulesConfig serverRules = new ServerRulesConfig();
    private MenusConfig menus = new MenusConfig();
    private CollectionsConfig collections = new CollectionsConfig();
    private FeaturesConfig features = new FeaturesConfig();

    public GeneralConfig general() {
        return this.general;
    }

    public MetricsConfig metrics() {
        return this.metrics;
    }

    public PresentationConfig presentation() {
        return this.presentation;
    }

    public HotReloadConfig hotReload() {
        return this.hotReload;
    }

    public DebugConfig debug() {
        return this.debug;
    }

    public Storage storage() {
        return this.storage;
    }

    public PerformanceConfig performance() {
        return this.performance;
    }

    public ServerRulesConfig serverRules() {
        return this.serverRules;
    }

    public MenusConfig menus() {
        return this.menus;
    }

    public CollectionsConfig collections() {
        return this.collections;
    }

    public FeaturesConfig features() {
        return this.features;
    }

    @Override
    public @NotNull String serializer() {
        return this.presentation.serializer();
    }

    @Override
    public int maxTotalPerPlayer() {
        return this.general.companionLimits.maxTotalPerPlayer;
    }

    @Override
    public int maxTotalPerPlayer(final @NotNull CompanionModel companionModel) {
        return companionModel == CompanionModel.FULL_BODY
                ? this.general.companionLimits.perModel.fullBody
                : this.general.companionLimits.perModel.headOnly;
    }

    @Override
    public boolean autoLoadCompanions() {
        return this.general.autoLoadCompanions;
    }

    @Override
    public boolean dispatchInteractEvent() {
        return this.performance.dispatchInteractEvent;
    }

    @Override
    public boolean openDashboardOnInteract() {
        return this.menus.rightClickDashboard;
    }

    @Override
    public int updateInterval() {
        return this.performance.updateInterval;
    }

    @Override
    public int viewerRefreshDelay() {
        return this.performance.viewerRefreshDelay;
    }

    @Override
    public int viewDistance() {
        return this.performance.viewDistance;
    }

    @ConfigSerializable
    @ConfigDescription("Master switch, server language, update notices, and companion limits.")
    public static final class GeneralConfig {
        @ConfigDoc(value = "Master switch for companion visuals and gameplay bonuses.", impact = "When false every world behaves like a hidden and bonus-disabled world; saved selections, progression, and particles are retained and return when the switch is turned back on.")
        private boolean enabled = true;

        @ConfigDoc(value = "Server default locale stored as the directly editable languages/<locale>.toml file.", impact = "Players may select their own language with /bytecompanions language; local translation edits are retained and missing entries use built-in English.")
        private String language = "en_US";

        @ConfigDoc(value = "Notify operators and players with bytecompanions.update about newer GitHub releases on join.", impact = "Checks run asynchronously once per hour; disabling stops checks and notices. No updates are downloaded or installed.")
        private boolean updateNotifications = true;

        @ConfigDoc(value = "Load the companion definitions from the companions folder when the runtime starts.", impact = "When false no companion definitions are registered and players cannot equip companions until the next runtime replacement with this switch on.")
        private boolean autoLoadCompanions = true;

        @ConfigDoc(value = "Limits on how many companions one player may equip at once.", impact = "Applies to new equip actions; companions already equipped above a lowered limit stay until removed.")
        private CompanionLimits companionLimits = new CompanionLimits();

        public boolean enabled() {
            return this.enabled;
        }

        public String language() {
            return this.language;
        }

        public boolean updateNotifications() {
            return this.updateNotifications;
        }

        public boolean autoLoadCompanions() {
            return this.autoLoadCompanions;
        }

        public CompanionLimits companionLimits() {
            return this.companionLimits;
        }
    }

    @ConfigSerializable
    @ConfigDescription("Companion count limits per player.")
    public static final class CompanionLimits {
        @ConfigDoc(value = "Maximum companions one player may equip at once; -1 removes the limit.", impact = "Checked on every equip action, outfit application, and starter restoration.")
        private int maxTotalPerPlayer = 2;

        @ConfigDoc(value = "Separate limits per companion model.", impact = "Each model limit applies on top of maxTotalPerPlayer.")
        private PerModel perModel = new PerModel();

        public int maxTotalPerPlayer() {
            return this.maxTotalPerPlayer;
        }

        public PerModel perModel() {
            return this.perModel;
        }

        @ConfigSerializable
        @ConfigDescription("Limits for full-body and head-only companion models.")
        public static final class PerModel {
            @ConfigDoc(value = "Maximum full-body companions one player may equip at once; -1 removes the limit.", impact = "Checked on every equip action.")
            private int fullBody = 1;

            @ConfigDoc(value = "Maximum head-only companions one player may equip at once; -1 removes the limit.", impact = "Checked on every equip action.")
            private int headOnly = 1;

            public int fullBody() {
                return this.fullBody;
            }

            public int headOnly() {
                return this.headOnly;
            }
        }
    }

    @ConfigSerializable
    @ConfigDescription("Anonymous usage statistics.")
    public static final class MetricsConfig {
        @ConfigDoc(value = "Submit anonymous ByteCompanions usage statistics to bStats.", impact = "Changes apply on the next runtime replacement; the global plugins/bStats/config.yml opt-out remains authoritative.")
        private boolean enabled = true;

        public boolean enabled() {
            return this.enabled;
        }
    }

    @ConfigSerializable
    @ConfigDescription("Console splash, command feedback sounds, and text formatting.")
    public static final class PresentationConfig {
        @ConfigDoc(value = "Print the ByteCompanions startup splash after services are ready.", impact = "The splash is console-only and is not replayed by runtime replacements.")
        private boolean splashScreen = true;

        @ConfigDoc(value = "Replace the splash art with one of the alternate pieces on one start in ten.", impact = "Only the art column changes; the detail rows stay the same. When false the standard art prints every time.")
        private boolean alternateSplashes = true;

        @ConfigDoc(value = "Play a short success or failure sound when a command reports its result to a player.", impact = "Applies to equip, unequip, and rejection feedback; menus never play sounds.")
        private boolean commandSounds = true;

        @ConfigDoc(value = "Text format for companion names, lore, and item blueprints: MINI_MESSAGE, LEGACY_AMPERSAND, or PLAIN.", impact = "Applies on the next runtime replacement to every companion and particle icon definition.")
        private String serializer = "MINI_MESSAGE";

        public boolean splashScreen() {
            return this.splashScreen;
        }

        public boolean alternateSplashes() {
            return this.alternateSplashes;
        }

        public boolean commandSounds() {
            return this.commandSounds;
        }

        public String serializer() {
            return this.serializer;
        }
    }

    @ConfigSerializable
    @ConfigDescription("Automatic reload of configuration, companion, feature, and language files.")
    public static final class HotReloadConfig {
        @ConfigDoc(value = "Apply stable file changes under the data folder automatically.", impact = "Invalid edits keep the last known good runtime; edits made while this is off are not replayed when it is turned back on.")
        private boolean enabled = true;

        @ConfigDoc(value = "Milliseconds between file scans, from 250 to 60000.", impact = "Lower values notice edits sooner and cost more disk reads; applies to the configuration and language watchers after the next poll.")
        private long pollIntervalMillis = 1000;

        @ConfigDoc(value = "Milliseconds a changed file must stay unchanged before it is applied, from 250 to 60000.", impact = "Coalesces editor save bursts so a half-written file is never installed.")
        private long cooldownMillis = 1500;

        @ConfigDoc(value = "Notify online players with bytecompanions.config when an automatic file reload succeeds or fails.", impact = "Uses one action bar line per reload; the console always logs the same outcome.")
        private boolean notifyOperators = true;

        public boolean enabled() {
            return this.enabled;
        }

        public long pollIntervalMillis() {
            return this.pollIntervalMillis;
        }

        public long cooldownMillis() {
            return this.cooldownMillis;
        }

        public boolean notifyOperators() {
            return this.notifyOperators;
        }
    }

    @ConfigSerializable
    @ConfigDescription("Diagnostic report uploads.")
    public static final class DebugConfig {
        @ConfigDoc(value = "Allow /bytecompanions debug dump upload=true to publish reports to mclo.gs after saving them locally.", impact = "When false reports stay local; the report file is written either way.")
        private boolean uploadEnabled = true;

        public boolean uploadEnabled() {
            return this.uploadEnabled;
        }
    }

    @ConfigSerializable
    @ConfigDescription("Player data storage backend and connection pool.")
    public static final class Storage {
        @ConfigDoc(value = "Storage backend for player companions, collections, progression, and particle choices: json, sqlite, h2, or mysql.", impact = "Applies on the next runtime replacement; data is not moved between backends automatically.")
        private String backend = "json";

        @ConfigDoc(value = "Connection pool limits for the sqlite, h2, and mysql backends.", impact = "Applies on the next runtime replacement; sqlite always uses one connection.")
        private Pool pool = new Pool();

        @ConfigDoc(value = "Embedded H2 database location.", impact = "Used only when backend is h2.")
        private H2 h2 = new H2();

        @ConfigDoc(value = "MySQL or MariaDB connection details.", impact = "Used only when backend is mysql; the password is redacted from diagnostic reports.")
        private Mysql mysql = new Mysql();

        public String backend() {
            return this.backend;
        }

        public Pool pool() {
            return this.pool;
        }

        public H2 h2() {
            return this.h2;
        }

        public Mysql mysql() {
            return this.mysql;
        }

        @ConfigSerializable
        @ConfigDescription("Connection pool limits.")
        public static final class Pool {
            @ConfigDoc(value = "Milliseconds to wait for a pooled connection; 0 uses the driver default, otherwise at least 250.", impact = "Longer waits delay player loads when the database is slow.")
            private int connectionTimeoutMillis = 5000;

            @ConfigDoc(value = "Maximum lifetime of a pooled connection in milliseconds; 0 disables retirement, otherwise at least 30000.", impact = "Shorter lifetimes reconnect more often.")
            private int maxLifetimeMillis = 1800000;

            @ConfigDoc(value = "Maximum simultaneous database connections in this pool.", impact = "Larger values allow more concurrent database work and consume more database resources.")
            private int maxPoolSize = 10;

            @ConfigDoc(value = "Idle connections kept open, from 0 to maxPoolSize.", impact = "Higher values keep connections warm for player joins.")
            private int minIdle = 1;

            public int connectionTimeoutMillis() {
                return this.connectionTimeoutMillis;
            }

            public int maxLifetimeMillis() {
                return this.maxLifetimeMillis;
            }

            public int maxPoolSize() {
                return this.maxPoolSize;
            }

            public int minIdle() {
                return this.minIdle;
            }
        }

        @ConfigSerializable
        @ConfigDescription("Embedded H2 database location.")
        public static final class H2 {
            @ConfigDoc(value = "Directory under plugins/ByteCompanions that holds the embedded database files.", impact = "Changing it starts an empty database at the new location; the old files are left in place.")
            private String directory = "storage";

            public String directory() {
                return this.directory;
            }
        }

        @ConfigSerializable
        @ConfigDescription("MySQL or MariaDB connection details.")
        public static final class Mysql {
            @ConfigDoc(value = "Database host name or address.", impact = "Used only when backend is mysql.")
            private String host = "127.0.0.1";

            @ConfigDoc(value = "Database port, from 1 to 65535.", impact = "Used only when backend is mysql.")
            private int port = 3306;

            @ConfigDoc(value = "Database name.", impact = "Used only when backend is mysql.")
            private String database = "bytecompanions";

            @ConfigDoc(value = "Database user.", impact = "Required when backend is mysql.")
            private String username = "";

            @ConfigDoc(value = "Database password.", impact = "Never written to diagnostic reports.")
            private String password = "";

            @ConfigDoc(value = "Require TLS for the database connection.", impact = "Used only when backend is mysql.")
            private boolean useSsl = false;

            public String host() {
                return this.host;
            }

            public int port() {
                return this.port;
            }

            public String database() {
                return this.database;
            }

            public String username() {
                return this.username;
            }

            public String password() {
                return this.password;
            }

            public boolean useSsl() {
                return this.useSsl;
            }
        }
    }

    @ConfigSerializable
    @ConfigDescription("Tick cadence, viewer tracking, and particle limits.")
    public static final class PerformanceConfig {
        @ConfigDoc(value = "Maximum particle intensity players may receive: FULL, REDUCED, or OFF.", impact = "REDUCED skips alternate complete animation frames; the more restrictive server or player choice applies.")
        private ViewerPreferences.ParticleIntensity maximumParticleIntensity = ViewerPreferences.ParticleIntensity.FULL;

        @ConfigDoc(value = "Fire CompanionPlayerInteractEvent for other plugins when a player clicks a companion.", impact = "Independent of the owner dashboard; enabling it attaches a packet listener per player on the next runtime replacement.")
        private boolean dispatchInteractEvent = false;

        @ConfigDoc(value = "Ticks between companion animation updates, at least 1.", impact = "Higher values reduce packet traffic and make movement less smooth; applies on the next runtime replacement.")
        private int updateInterval = 3;

        @ConfigDoc(value = "Ticks between viewer distance checks, at least 1.", impact = "Higher values delay companions appearing for approaching players.")
        private int viewerRefreshDelay = 3;

        @ConfigDoc(value = "Blocks within which other players see a companion, 0 or more.", impact = "Larger distances send companion packets to more viewers.")
        private int viewDistance = 8;

        public ViewerPreferences.ParticleIntensity maximumParticleIntensity() {
            return this.maximumParticleIntensity;
        }

        public boolean dispatchInteractionEvent() {
            return this.dispatchInteractEvent;
        }

        public int updateInterval() {
            return this.updateInterval;
        }

        public int viewerRefreshDelay() {
            return this.viewerRefreshDelay;
        }

        public int viewDistance() {
            return this.viewDistance;
        }
    }

    @ConfigSerializable
    @ConfigDescription("World and region restrictions.")
    public static final class ServerRulesConfig {
        @ConfigDoc(value = "World names where companion visuals are hidden.", impact = "Saved selections and bonuses are retained; visuals return when the player leaves the world.")
        private List<String> hiddenWorlds = List.of();

        @ConfigDoc(value = "World names where companion attributes, effects, reward bonuses, and automatic companion XP are suspended.", impact = "Booster timers keep running; explicit administrator grants still apply.")
        private List<String> bonusDisabledWorlds = List.of();

        @ConfigDoc(value = "Honor the bytecompanions-visuals and bytecompanions-bonuses flags when WorldGuard is installed.", impact = "Without WorldGuard the world lists alone apply.")
        private boolean worldguard = true;

        public List<String> hiddenWorlds() {
            return this.hiddenWorlds;
        }

        public List<String> bonusDisabledWorlds() {
            return this.bonusDisabledWorlds;
        }

        public boolean worldguard() {
            return this.worldguard;
        }
    }

    @ConfigSerializable
    @ConfigDescription("Dashboard shortcuts.")
    public static final class MenusConfig {
        @ConfigDoc(value = "Open the companion dashboard when its owner right-clicks it with the main hand.", impact = "Independent of performance.dispatchInteractEvent; applies on the next runtime replacement.")
        private boolean rightClickDashboard = true;

        public boolean rightClickDashboard() {
            return this.rightClickDashboard;
        }
    }

    @ConfigSerializable
    @ConfigDescription("Personal collections, outfits, trials, and personality.")
    public static final class CollectionsConfig {
        @ConfigDoc(value = "Maximum saved outfits per player, from 1 to 32.", impact = "Lowering it keeps existing outfits but blocks new saves above the limit.")
        private int maximumOutfits = 8;

        @ConfigDoc(value = "Private locked-companion preview duration, from 5 to 120 seconds.", impact = "Applies to trials started after the change.")
        private int trialSeconds = 30;

        @ConfigDoc(value = "Allow pet, wave, sit, stay, and call interactions.", impact = "When false the dashboard hides the actions and /bytecompanions interact is rejected.")
        private boolean personalityEnabled = true;

        @ConfigDoc(value = "Allow occasional idle reactions while a companion is stationary.", impact = "Applies on the next runtime replacement.")
        private boolean idleReactions = true;

        public int maximumOutfits() {
            return this.maximumOutfits;
        }

        public int trialSeconds() {
            return this.trialSeconds;
        }

        public boolean personalityEnabled() {
            return this.personalityEnabled;
        }

        public boolean idleReactions() {
            return this.idleReactions;
        }
    }

    @ConfigSerializable
    @ConfigDescription("Feature packs; disabled packs hide their commands and keep their saved state.")
    public static final class FeaturesConfig {
        @ConfigDoc(value = "Enable companion attribute modifiers.", impact = "Definitions live in features/attributes/config.toml; applies on the next runtime replacement.")
        private boolean attributes = false;

        @ConfigDoc(value = "Enable companion buffs such as drop and experience bonuses.", impact = "Definitions live in features/buffs/config.toml; applies on the next runtime replacement.")
        private boolean buffs = false;

        @ConfigDoc(value = "Enable static decoration companions.", impact = "Definitions live in features/decorations/config.toml; applies on the next runtime replacement.")
        private boolean decorations = true;

        @ConfigDoc(value = "Enable companion potion effects.", impact = "Definitions live in features/effects/config.toml; applies on the next runtime replacement.")
        private boolean effects = false;

        @ConfigDoc(value = "Enable companion particle effects.", impact = "Icons live in features/particles/icons.toml and settings in features/particles/config.toml; applies on the next runtime replacement.")
        private boolean particles = true;

        @ConfigDoc(value = "Enable companion levels and optional bonus upgrades.", impact = "Progression rules and upgrade costs are configured in features/progression/config.toml.")
        private boolean progression = false;

        @ConfigDoc(value = "Enable personal and server-wide companion boosters.", impact = "Booster definitions are configured in features/progression/config.toml.")
        private boolean boosters = false;

        public boolean enabled(String feature) {
            return switch (feature) {
                case "attributes" -> this.attributes;
                case "buffs" -> this.buffs;
                case "decorations" -> this.decorations;
                case "effects" -> this.effects;
                case "particles" -> this.particles;
                case "progression" -> this.progression;
                case "boosters" -> this.boosters;
                default -> false;
            };
        }
    }
}
