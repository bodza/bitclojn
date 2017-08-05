package org.bitcoinj.wallet;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.bitcoinj.core.*;

/**
 * A filtering coin selector delegates to another coin selector, but won't select outputs spent by the given transactions.
 */
public class FilteringCoinSelector implements CoinSelector
{
    protected CoinSelector delegate;
    protected HashSet<TransactionOutPoint> spent = new HashSet<>();

    public FilteringCoinSelector(CoinSelector delegate)
    {
        this.delegate = delegate;
    }

    public void excludeOutputsSpentBy(Transaction tx)
    {
        for (TransactionInput input : tx.getInputs())
            spent.add(input.getOutpoint());
    }

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates)
    {
        Iterator<TransactionOutput> iter = candidates.iterator();
        while (iter.hasNext())
        {
            TransactionOutput output = iter.next();
            if (spent.contains(output.getOutPointFor()))
                iter.remove();
        }
        return delegate.select(target, candidates);
    }
}
