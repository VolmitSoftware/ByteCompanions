package com.volmit.bytecompanions.features.particles.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;

@ConfigSerializable
public final class StorageConfiguration {

    @Setting("type")
    private String type = "H2";

    @Setting("pool")
    private Pool pool = new Pool();

    @Setting("credentials")
    private Credentials credentials = new Credentials();

    @Setting("h2")
    private H2 h2 = new H2();

    public String type() {
        return this.type;
    }

    public Pool pool() {
        return this.pool;
    }

    public Credentials credentials() {
        return this.credentials;
    }

    public H2 h2() {
        return this.h2;
    }

    @ConfigSerializable
    public static final class Pool {

        @Setting("connection-timeout")
        private int connectionTimeout = 5000;

        @Setting("max-lifetime")
        private int maxLifetime = 1800000;

        @Setting("max-pool-size")
        private int maxPoolSize = 10;

        @Setting("min-idle")
        private int minIdle = 1;

        public int connectionTimeout() {
            return this.connectionTimeout;
        }

        public int maxLifetime() {
            return this.maxLifetime;
        }

        public int maxPoolSize() {
            return this.maxPoolSize;
        }

        public int minIdle() {
            return this.minIdle;
        }
    }

    @ConfigSerializable
    public static final class Credentials {

        @Setting("host")
        private String host = "localhost";

        @Setting("port")
        private int port = 3306;

        @Setting("database")
        private String database = "bytecompanions";

        @Setting("username")
        private String username = "root";

        @Setting("password")
        private String password = "ilovefory";

        @Setting("use-ssl")
        private boolean useSSL = false;

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

        public boolean useSSL() {
            return this.useSSL;
        }
    }

    @ConfigSerializable
    public static final class H2 {

        @Setting("path")
        private String parentPath = "storage";

        public String parentPath() {
            return this.parentPath;
        }
    }

}
