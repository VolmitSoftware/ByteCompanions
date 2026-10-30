package com.volmit.bytecompanions.platform.paper.progression;

import com.volmit.bytecompanions.platform.paper.storage.StorageException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProgressionStore {
    void initialize() throws StorageException;

    <T> T transaction(Work<T> work) throws StorageException;

    interface Work<T> {
        T apply(Transaction transaction) throws Exception;
    }

    interface Transaction {
        ProgressionService.PlayerState player(UUID player) throws Exception;

        void player(UUID player, ProgressionService.PlayerState state) throws Exception;

        ProgressionService.GlobalState global() throws Exception;

        void global(ProgressionService.GlobalState state) throws Exception;

        void create(ProgressionService.PurchaseJournal journal) throws Exception;

        void journal(ProgressionService.PurchaseJournal journal) throws Exception;

        ProgressionService.PurchaseJournal journal(String id) throws Exception;

        List<ProgressionService.PurchaseJournal> unresolved() throws Exception;

        Map<UUID, ProgressionService.PlayerState> players() throws Exception;
    }
}
