package org.bitcoinj.wallet;

import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;

/**
 * Factory interface for creation keychains while de-serializing a wallet.
 */
public interface KeyChainFactory
{
    /**
     * Make a keychain (but not a watching one).
     *
     * @param key The protobuf for the root key.
     * @param firstSubKey The protobuf for the first child key (normally the parent of the external subchain).
     * @param seed The seed.
     * @param crypter The encrypted/decrypter.
     * @param isMarried Whether the keychain is leading in a marriage.
     */
    DeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed, KeyCrypter crypter, boolean isMarried);

    /**
     * Make a watching keychain.
     *
     * isMarried and isFollowingKey must not be true at the same time.
     *
     * @param key The protobuf for the account key.
     * @param firstSubKey The protobuf for the first child key (normally the parent of the external subchain).
     * @param accountKey The account extended public key.
     * @param isFollowingKey Whether the keychain is following in a marriage.
     * @param isMarried Whether the keychain is leading in a marriage.
     */
    DeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey, boolean isFollowingKey, boolean isMarried)
        throws UnreadableWalletException;
}
