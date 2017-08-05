package org.bitcoinj.wallet;

import org.bitcoinj.crypto.*;

/**
 * Default factory for creating keychains while de-serializing.
 */
public class DefaultKeyChainFactory implements KeyChainFactory
{
    @Override
    public DeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed, KeyCrypter crypter, boolean isMarried)
    {
        return isMarried ? new MarriedKeyChain(seed, crypter) : new DeterministicKeyChain(seed, crypter);
    }

    @Override
    public DeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey, boolean isFollowingKey, boolean isMarried)
        throws UnreadableWalletException
    {
        if (!accountKey.getPath().equals(DeterministicKeyChain.ACCOUNT_ZERO_PATH))
            throw new UnreadableWalletException("Expecting account key but found key with path: " + HDUtils.formatPath(accountKey.getPath()));

        return isMarried ? new MarriedKeyChain(accountKey) : new DeterministicKeyChain(accountKey, isFollowingKey);
    }
}
