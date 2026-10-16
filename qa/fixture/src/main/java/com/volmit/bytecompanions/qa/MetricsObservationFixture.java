package com.volmit.bytecompanions.qa;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

final class MetricsObservationFixture {
    private static final Map<Object, Integer> observed = new IdentityHashMap<>();

    private MetricsObservationFixture() {
    }

    static synchronized String inspect() {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("ByteCompanions");
            Object service = service(plugin);
            Map<String, Object> state = new LinkedHashMap<>();
            synchronized (service) {
                Object active = field(service, "metrics");
                if (active != null) observed.computeIfAbsent(active, ignored -> observed.size() + 1);
                state.put("serviceIdentity", System.identityHashCode(service));
                state.put("initialized", active != null);
                state.put("activeIdentity", active == null ? null : observed.get(active));
                state.put("closed", field(service, "closed"));
                List<Map<String, Object>> instances = new ArrayList<>();
                for (Map.Entry<Object, Integer> entry : observed.entrySet()) {
                    Object base = field(entry.getKey(), "metricsBase");
                    ScheduledThreadPoolExecutor scheduler = (ScheduledThreadPoolExecutor) field(base, "scheduler");
                    instances.add(Map.of("identity", entry.getValue(), "serviceId", field(base, "serviceId"),
                            "globalEnabled", field(base, "enabled"), "schedulerShutdown", scheduler.isShutdown(),
                            "scheduledTasks", scheduler.getQueue().size()));
                }
                state.put("instances", instances);
            }
            return "BCQA METRICS " + new Gson().toJson(state);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not inspect metrics state", failure);
        }
    }

    private static Object service(Plugin plugin) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.volmit.bytecompanions.platform.paper.metrics.MetricsService", true,
                plugin.getClass().getClassLoader());
        try {
            return MethodHandles.privateLookupIn(plugin.getClass(), MethodHandles.lookup())
                    .findGetter(plugin.getClass(), "metricsService", type).invoke(plugin);
        } catch (Throwable failure) {
            throw new ReflectiveOperationException("Could not read the metrics service", failure);
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
