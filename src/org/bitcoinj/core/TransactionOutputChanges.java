package org.bitcoinj.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * <p>TransactionOutputChanges represents a delta to the set of unspent outputs.  It used as a return value for
 * {@link AbstractBlockChain#connectTransactions(int, Block)}.  It contains the full list of transaction outputs created
 * and spent in a block.  It DOES contain outputs created that were spent later in the block, as those are needed for
 * BIP30 (no duplicate txid creation if the previous one was not fully spent prior to this block) verification.</p>
 */
public class TransactionOutputChanges
{
    public final List<UTXO> txOutsCreated;
    public final List<UTXO> txOutsSpent;

    public TransactionOutputChanges(List<UTXO> txOutsCreated, List<UTXO> txOutsSpent)
    {
        this.txOutsCreated = txOutsCreated;
        this.txOutsSpent = txOutsSpent;
    }

    private static final int read4x8le(InputStream is)
        throws IOException
    {
        return (is.read() & 0xff) | ((is.read() & 0xff) << 8) | ((is.read() & 0xff) << 16) | ((is.read() & 0xff) << 24);
    }

    public TransactionOutputChanges(InputStream is)
        throws IOException
    {
        int nCreated = read4x8le(is);
        txOutsCreated = new LinkedList<>();
        for (int i = 0; i < nCreated; i++)
            txOutsCreated.add(new UTXO(is));

        int nSpent = read4x8le(is);
        txOutsSpent = new LinkedList<>();
        for (int i = 0; i < nSpent; i++)
            txOutsSpent.add(new UTXO(is));
    }

    private static final void write4x8le(OutputStream os, int n)
        throws IOException
    {
        os.write(0xff & n);
        os.write(0xff & (n >> 8));
        os.write(0xff & (n >> 16));
        os.write(0xff & (n >> 24));
    }

    public void serializeToStream(OutputStream os)
        throws IOException
    {
        write4x8le(os, txOutsCreated.size());
        for (UTXO output : txOutsCreated)
            output.serializeToStream(os);

        write4x8le(os, txOutsSpent.size());
        for (UTXO output : txOutsSpent)
            output.serializeToStream(os);
    }
}
