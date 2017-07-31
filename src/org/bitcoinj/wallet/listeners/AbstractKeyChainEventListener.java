package org.bitcoinj.wallet.listeners;

import org.bitcoinj.core.ECKey;

import java.util.List;

public class AbstractKeyChainEventListener implements KeyChainEventListener
{
    @Override
    public void onKeysAdded(List<ECKey> keys)
    {
    }
}
