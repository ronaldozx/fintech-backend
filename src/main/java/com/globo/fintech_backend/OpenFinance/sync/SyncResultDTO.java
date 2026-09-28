package com.globo.fintech_backend.OpenFinance.sync;

public record SyncResultDTO(int connections, int imported, int transferPairs, int failed) {

    public SyncResultDTO(int connections, int imported) {
        this(connections, imported, 0, 0);
    }

    public SyncResultDTO withTransferPairs(int pairs) {
        return new SyncResultDTO(connections, imported, pairs, failed);
    }
}
