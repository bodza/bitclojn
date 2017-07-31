package org.bitcoinj.core.listeners;

import java.util.Set;

import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;

/**
 * <p>Implementors can listen to events for peers being discovered.</p>
 */
public interface PeerDiscoveredEventListener
{
    /**
     * <p>Called when peers are discovered, this happens at startup of {@link PeerGroup}
     * or if we run out of suitable {@link Peer}s to connect to.</p>
     *
     * @param peerAddresses The set of discovered {@link PeerAddress}es.
     */
    void onPeersDiscovered(Set<PeerAddress> peerAddresses);
}
