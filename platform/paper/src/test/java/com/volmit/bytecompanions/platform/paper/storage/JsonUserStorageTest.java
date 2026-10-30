package com.volmit.bytecompanions.platform.paper.storage;

import com.volmit.bytecompanions.core.concurrent.VirtualThreadAsyncExecutor;
import com.volmit.bytecompanions.providers.storage.json.JsonUserStorage;
import com.volmit.bytecompanions.spi.storage.entry.UserEntry;
import com.volmit.bytecompanions.spi.storage.view.UserView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUserStorageTest {
    @TempDir
    Path root;
    private final UUID player = UUID.randomUUID();
    private VirtualThreadAsyncExecutor executor;
    private JsonUserStorage storage;

    @BeforeEach
    void open() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        executor = new VirtualThreadAsyncExecutor();
        storage = new JsonUserStorage(logger, root, executor);
        storage.initialize();
    }

    @AfterEach
    void close() throws Exception {
        storage.closeAsync().get(5, TimeUnit.SECONDS);
        executor.shutdown();
    }

    @Test
    void selectionsRoundTripThroughOneDocumentPerPlayer() {
        assertNull(storage.fetch(player));
        storage.save(new UserEntry(player, "Tester", List.of("bee", "fox")));
        UserView view = storage.fetch(player);
        assertEquals("Tester", view.playerName());
        assertEquals(List.of("bee", "fox"), view.companionTypeIds());
        assertTrue(Files.exists(root.resolve("companions").resolve(player + ".json")));
        storage.equip(new UserEntry(player, "Tester"), "copper");
        assertEquals(List.of("bee", "fox", "copper"), storage.fetch(player).companionTypeIds());
        storage.unequip(player, "fox");
        assertEquals(List.of("bee", "copper"), storage.fetch(player).companionTypeIds());
        assertEquals(2, storage.delete(player));
        assertNull(storage.fetch(player));
        assertEquals(0, storage.delete(player));
    }

    @Test
    void anUnreadableDocumentFallsBackToTheLastGoodCopy() throws Exception {
        storage.save(new UserEntry(player, "Tester", List.of("bee")));
        storage.save(new UserEntry(player, "Tester", List.of("bee", "fox")));
        Path document = root.resolve("companions").resolve(player + ".json");
        Files.writeString(document, "{ not json", StandardCharsets.UTF_8);
        assertEquals(List.of("bee"), storage.fetch(player).companionTypeIds());
    }

    @Test
    void undecodableBytesFallBackAndNeverReplaceTheLastGoodCopy() throws Exception {
        storage.save(new UserEntry(player, "Tester", List.of("bee")));
        storage.save(new UserEntry(player, "Tester", List.of("bee", "fox")));
        Path document = root.resolve("companions").resolve(player + ".json");
        Path lastGood = root.resolve("companions").resolve(player + ".json.last-good");
        Files.write(document, new byte[]{(byte) 0xC3, (byte) 0x28, (byte) 0xFF});
        assertEquals(List.of("bee"), storage.fetch(player).companionTypeIds());
        storage.save(new UserEntry(player, "Tester", List.of("cat")));
        assertEquals(List.of("cat"), storage.fetch(player).companionTypeIds());
        assertTrue(Files.readString(lastGood, StandardCharsets.UTF_8).contains("bee"));
        Files.writeString(document, "{ not json", StandardCharsets.UTF_8);
        storage.save(new UserEntry(player, "Tester", List.of("owl")));
        assertTrue(Files.readString(lastGood, StandardCharsets.UTF_8).contains("bee"));
        assertEquals(List.of("owl"), storage.fetch(player).companionTypeIds());
    }

    @Test
    void asynchronousWritesRunOffTheCallerAndSerializePerPlayer() throws Exception {
        Thread caller = Thread.currentThread();
        storage.async().save(new UserEntry(player, "Tester", List.of("bee"))).get(5, TimeUnit.SECONDS);
        storage.async().equip(new UserEntry(player, "Tester"), "fox").get(5, TimeUnit.SECONDS);
        Thread worker = storage.submit(player, Thread::currentThread).get(5, TimeUnit.SECONDS);
        assertNotSame(caller, worker);
        assertEquals(List.of("bee", "fox"), storage.async().fetch(player).get(5, TimeUnit.SECONDS).companionTypeIds());
        assertFalse(storage.operationSnapshot().closed());
    }
}
