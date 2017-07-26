package org.bitcoinj.core.listeners;

import org.bitcoinj.core.Peer;

/**
 * <p>Implementors can listen to events indicating a new peer connecting.</p>
 */
public interface PeerConnectedEventListener {

    /**
     * Called when a peer is connected. If this listener is registered to a {@link Peer} instead of a {@link PeerGroup},
     * peerCount will always be 1.
     *
     * @param peer
     * @param peerCount the total number of connected peers
     */
    void onPeerConnected(Peer peer, int peerCount);
}
