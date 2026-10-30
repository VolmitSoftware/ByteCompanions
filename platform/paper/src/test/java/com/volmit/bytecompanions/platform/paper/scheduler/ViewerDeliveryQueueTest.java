package com.volmit.bytecompanions.platform.paper.scheduler;

import org.junit.jupiter.api.Test;
import com.volmit.bytecompanions.core.companion.ViewerDeliveryQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerDeliveryQueueTest {
    @Test
    void destroyInvalidatesQueuedSpawnButPreservesOrderedRemovalBeforeClose() {
        List<String> packets = new ArrayList<>();
        ViewerDeliveryQueue queue = new ViewerDeliveryQueue(8, failure -> {});
        AtomicBoolean active = new AtomicBoolean(true);
        queue.submit(() -> {
            if (active.get()) {
                packets.add("spawn");
            }
        });
        active.set(false);
        queue.submit(() -> packets.add("remove"));
        CompletableFuture<Void> closed = queue.close();
        assertFalse(closed.isDone());
        queue.drain();
        closed.join();
        assertEquals(List.of("remove"), packets);
        assertFalse(queue.submit(() -> packets.add("late")));
    }

    @Test
    void overflowRejectsWithOneReportAndAcceptedPacketsStillDrainInOrder() {
        List<Throwable> failures = new ArrayList<>();
        List<Integer> packets = new ArrayList<>();
        ViewerDeliveryQueue queue = new ViewerDeliveryQueue(2, failures::add);
        assertTrue(queue.submit(() -> packets.add(1)));
        assertTrue(queue.submit(() -> packets.add(2)));
        assertFalse(queue.submit(() -> packets.add(3)));
        assertFalse(queue.submit(() -> packets.add(4)));
        assertEquals(1, failures.size());
        assertTrue(failures.getFirst() instanceof RejectedExecutionException);
        queue.drain();
        assertEquals(List.of(1, 2), packets);
        assertTrue(queue.submit(() -> packets.add(5)));
        queue.drain();
        assertEquals(List.of(1, 2, 5), packets);
    }

    @Test
    void anEarlierDeliveryFailureDoesNotFailLaterBarriers() {
        List<Throwable> failures = new ArrayList<>();
        ViewerDeliveryQueue queue = new ViewerDeliveryQueue(4, failures::add);
        queue.submit(() -> { throw new IllegalStateException("native send failed"); });
        queue.drain();
        assertEquals(1, failures.size());
        CompletableFuture<Void> closed = queue.close();
        queue.drain();
        closed.join();
    }

    @Test
    void disconnectedSessionReleasesExistingAndLaterBarriersWithoutSending() {
        List<String> packets = new ArrayList<>();
        ViewerDeliveryQueue queue = new ViewerDeliveryQueue(4, failure -> {});
        queue.submit(() -> packets.add("disconnected"));
        CompletableFuture<Void> barrier = queue.flush();
        queue.retire();
        barrier.join();
        assertTrue(queue.close().isDone());
        queue.drain();
        assertTrue(packets.isEmpty());
    }

    @Test
    void failedDeliveryDoesNotPreventFinalRemovalOrHideFailureFromBarrier() {
        List<Throwable> failures = new ArrayList<>();
        List<String> packets = new ArrayList<>();
        ViewerDeliveryQueue queue = new ViewerDeliveryQueue(4, failures::add);
        queue.submit(() -> { throw new IllegalStateException("native send failed"); });
        queue.submit(() -> packets.add("remove"));
        CompletableFuture<Void> closed = queue.close();
        queue.drain();
        assertEquals(List.of("remove"), packets);
        assertEquals(1, failures.size());
        assertThrows(CompletionException.class, closed::join);
    }
}
