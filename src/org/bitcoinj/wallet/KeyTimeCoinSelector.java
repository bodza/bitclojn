package org.bitcoinj.wallet;

import java.util.LinkedList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;

/**
 * A coin selector that takes all coins assigned to keys created before the given timestamp.
 * Used as part of the implementation of {@link Wallet#setKeyRotationTime(java.util.Date)}.
 */
public class KeyTimeCoinSelector implements CoinSelector
{
    private static final Logger log = LoggerFactory.getLogger(KeyTimeCoinSelector.class);

    /** A number of inputs chosen to avoid hitting {@link org.bitcoinj.core.Transaction#MAX_STANDARD_TX_SIZE}. */
    public static final int MAX_SIMULTANEOUS_INPUTS = 600;

    private final long unixTimeSeconds;
    private final Wallet wallet;
    private final boolean ignorePending;

    public KeyTimeCoinSelector(Wallet wallet, long unixTimeSeconds, boolean ignorePending)
    {
        this.unixTimeSeconds = unixTimeSeconds;
        this.wallet = wallet;
        this.ignorePending = ignorePending;
    }

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates)
    {
        try
        {
            LinkedList<TransactionOutput> gathered = Lists.newLinkedList();
            Coin valueGathered = Coin.ZERO;
            for (TransactionOutput output : candidates)
            {
                if (ignorePending && !isConfirmed(output))
                    continue;

                // Find the key that controls output, assuming it's a regular pay-to-pubkey or pay-to-address output.
                // We ignore any other kind of exotic output on the assumption we can't spend it ourselves.
                final Script scriptPubKey = output.getScriptPubKey();
                ECKey controllingKey;
                if (scriptPubKey.isSentToRawPubKey())
                    controllingKey = wallet.findKeyFromPubKey(scriptPubKey.getPubKey());
                else if (scriptPubKey.isSentToAddress())
                    controllingKey = wallet.findKeyFromPubHash(scriptPubKey.getPubKeyHash());
                else
                {
                    log.info("Skipping tx output {} because it's not of simple form.", output);
                    continue;
                }

                checkNotNull(controllingKey, "Coin selector given output as candidate for which we lack the key");
                if (unixTimeSeconds <= controllingKey.getCreationTimeSeconds())
                    continue;

                // It's older than the cutoff time so select.
                valueGathered = valueGathered.add(output.getValue());
                gathered.push(output);
                if (MAX_SIMULTANEOUS_INPUTS <= gathered.size())
                {
                    log.warn("Reached {} inputs, going further would yield a tx that is too large, stopping here.", gathered.size());
                    break;
                }
            }
            return new CoinSelection(valueGathered, gathered);
        }
        catch (ScriptException e)
        {
            throw new RuntimeException(e); // We should never have problems understanding scripts in our wallet.
        }
    }

    private boolean isConfirmed(TransactionOutput output)
    {
        return output.getParentTransaction().getConfidence().getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING);
    }
}
