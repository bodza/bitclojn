package org.bitcoinj.core.listeners;

import org.bitcoinj.core.Peer;

/**
 * <p>Implementors can listen to events indicating a peer disconnecting.</p>
 */
public interface PeerDisconnectedEventListener
{
    /**
     * Called when a peer is disconnected.  Note that this won't be called if the listener is registered on
     * a {@link PeerGroup} and the group is in the process of shutting down.  If this listener is registered to
     * a {@link Peer} instead of a {@link PeerGroup}, peerCount will always be 0.  This handler can be called
     * without a corresponding invocation of onPeerConnected if the initial connection is never successful.
     *
     * @param peer
     * @param peerCount The total number of connected peers.
     */
    void onPeerDisconnected(Peer peer, int peerCount);
}
