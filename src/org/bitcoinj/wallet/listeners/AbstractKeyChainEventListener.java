package org.bitcoinj.wallet.listeners;

import java.util.List;

import org.bitcoinj.core.ECKey;

public class AbstractKeyChainEventListener implements KeyChainEventListener
{
    @Override
    public void onKeysAdded(List<ECKey> keys)
    {
    }
}
