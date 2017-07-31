package org.bitcoinj.core;

import java.util.List;

/**
 * Contains minimal data neccessary to disconnect/connect the transactions
 * in the stored block at will.  Can either store the full set of
 * transactions (if the inputs for the block have not been tested to work)
 * or the set of transaction outputs created/destroyed when the block is
 * connected.
 */
public class StoredUndoableBlock
{
    Sha256Hash blockHash;

    // Only one of either txOutChanges or transactions will be set.
    private TransactionOutputChanges txOutChanges;
    private List<Transaction> transactions;

    public StoredUndoableBlock(Sha256Hash hash, TransactionOutputChanges txOutChanges)
    {
        this.blockHash = hash;
        this.transactions = null;
        this.txOutChanges = txOutChanges;
    }

    public StoredUndoableBlock(Sha256Hash hash, List<Transaction> transactions)
    {
        this.blockHash = hash;
        this.txOutChanges = null;
        this.transactions = transactions;
    }

    /**
     * Get the transaction output changes if they have been calculated, otherwise null.
     * Only one of this and getTransactions() will return a non-null value.
     */
    public TransactionOutputChanges getTxOutChanges()
    {
        return txOutChanges;
    }

    /**
     * Get the full list of transactions if it is stored, otherwise null.
     * Only one of this and getTxOutChanges() will return a non-null value.
     */
    public List<Transaction> getTransactions()
    {
        return transactions;
    }

    /**
     * Get the hash of the represented block.
     */
    public Sha256Hash getHash()
    {
        return blockHash;
    }

    @Override
    public int hashCode()
    {
        return blockHash.hashCode();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return getHash().equals(((StoredUndoableBlock)o).getHash());
    }

    @Override
    public String toString()
    {
        return "Undoable Block " + blockHash;
    }
}
