package org.bitcoinj.core;

import java.io.*;
import java.math.*;
import java.util.Locale;

import com.google.common.base.Objects;

import org.bitcoinj.script.*;

// TODO: Fix this class: should not talk about addresses, height should be optional/support mempool height etc.

/**
 * A UTXO message contains the information necessary to check a spending transaction.
 * It avoids having to store the entire parentTransaction just to get the hash and index.
 * Useful when working with free standing outputs.
 */
public class UTXO implements Serializable
{
    private Coin value;
    private Script script;
    private Sha256Hash hash;
    private long index;
    private int height;
    private boolean coinbase;
    private String address;

    /**
     * Creates a stored transaction output.
     *
     * @param hash     The hash of the containing transaction.
     * @param index    The outpoint.
     * @param value    The value available.
     * @param height   The height this output was created in.
     * @param coinbase The coinbase flag.
     */
    public UTXO(Sha256Hash hash, long index, Coin value, int height, boolean coinbase, Script script)
    {
        this.hash = hash;
        this.index = index;
        this.value = value;
        this.height = height;
        this.script = script;
        this.coinbase = coinbase;
        this.address = "";
    }

    /**
     * Creates a stored transaction output.
     *
     * @param hash     The hash of the containing transaction.
     * @param index    The outpoint.
     * @param value    The value available.
     * @param height   The height this output was created in.
     * @param coinbase The coinbase flag.
     * @param address  The address.
     */
    public UTXO(Sha256Hash hash, long index, Coin value, int height, boolean coinbase, Script script, String address)
    {
        this(hash, index, value, height, coinbase, script);
        this.address = address;
    }

    public UTXO(InputStream in)
        throws IOException
    {
        deserializeFromStream(in);
    }

    /** The value which this Transaction output holds. */
    public Coin getValue()
    {
        return value;
    }

    /** The Script object which you can use to get address, script bytes or script type. */
    public Script getScript()
    {
        return script;
    }

    /** The hash of the transaction which holds this output. */
    public Sha256Hash getHash()
    {
        return hash;
    }

    /** The index of this output in the transaction which holds it. */
    public long getIndex()
    {
        return index;
    }

    /** Gets the height of the block that created this output. */
    public int getHeight()
    {
        return height;
    }

    /** Gets the flag of whether this was created by a coinbase tx. */
    public boolean isCoinbase()
    {
        return coinbase;
    }

    /** The address of this output, can be the empty string if none was provided at construction time or was deserialized. */
    public String getAddress()
    {
        return address;
    }

    @Override
    public String toString()
    {
        return String.format(Locale.US, "Stored TxOut of %s (%s:%d)", value.toFriendlyString(), hash, index);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(getIndex(), getHash());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UTXO other = (UTXO)o;
        return (getIndex() == other.getIndex() && getHash().equals(other.getHash()));
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
        Utils.uint64ToByteStreamLE(BigInteger.valueOf(value.value), os);

        byte[] scriptBytes = script.getProgram();
        write4x8le(os, scriptBytes.length);
        os.write(scriptBytes);

        os.write(hash.getBytes());
        Utils.uint32ToByteStreamLE(index, os);

        write4x8le(os, height);
        os.write(new byte[] { (byte)(coinbase ? 1 : 0) });
    }

    private static final int read4x8le(InputStream is)
        throws IOException
    {
        return (is.read() & 0xff) | ((is.read() & 0xff) << 8) | ((is.read() & 0xff) << 16) | ((is.read() & 0xff) << 24);
    }

    public void deserializeFromStream(InputStream is)
        throws IOException
    {
        byte[] valueBytes = new byte[8];
        if (is.read(valueBytes, 0, 8) != 8)
            throw new EOFException();
        value = Coin.valueOf(Utils.readInt64(valueBytes, 0));

        int scriptBytesLength = read4x8le(is);
        byte[] scriptBytes = new byte[scriptBytesLength];
        if (is.read(scriptBytes) != scriptBytesLength)
            throw new EOFException();
        script = new Script(scriptBytes);

        byte[] hashBytes = new byte[32];
        if (is.read(hashBytes) != 32)
            throw new EOFException();
        hash = Sha256Hash.wrap(hashBytes);

        byte[] indexBytes = new byte[4];
        if (is.read(indexBytes) != 4)
            throw new EOFException();
        index = Utils.readUint32(indexBytes, 0);

        height = read4x8le(is);

        byte[] coinbaseByte = new byte[1];
        is.read(coinbaseByte);
        coinbase = (coinbaseByte[0] == 1);
    }

    private void writeObject(ObjectOutputStream o)
        throws IOException
    {
        serializeToStream(o);
    }

    private void readObject(ObjectInputStream o)
        throws IOException, ClassNotFoundException
    {
        deserializeFromStream(o);
    }
}
