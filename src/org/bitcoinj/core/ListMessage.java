package org.bitcoinj.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>Abstract superclass of classes with list based payload, i.e. InventoryMessage and GetDataMessage.</p>
 *
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public abstract class ListMessage extends Message
{
    public static final long MAX_INVENTORY_ITEMS = 50000;

    private long arrayLen;
    // For some reason the compiler complains if this is inside InventoryItem.
    protected List<InventoryItem> items;

    public ListMessage(NetworkParameters params, byte[] bytes)
        throws ProtocolException
    {
        super(params, bytes, 0);
    }

    public ListMessage(NetworkParameters params, byte[] payload, MessageSerializer serializer, int length)
        throws ProtocolException
    {
        super(params, payload, 0, serializer, length);
    }

    public ListMessage(NetworkParameters params)
    {
        super(params);

        items = new ArrayList<>();
        length = 1; // length of 0 varint
    }

    public List<InventoryItem> getItems()
    {
        return Collections.unmodifiableList(items);
    }

    public void addItem(InventoryItem item)
    {
        unCache();
        length -= VarInt.sizeOf(items.size());
        items.add(item);
        length += VarInt.sizeOf(items.size()) + InventoryItem.MESSAGE_LENGTH;
    }

    public void removeItem(int index)
    {
        unCache();
        length -= VarInt.sizeOf(items.size());
        items.remove(index);
        length += VarInt.sizeOf(items.size()) - InventoryItem.MESSAGE_LENGTH;
    }

    @Override
    protected void parse()
        throws ProtocolException
    {
        arrayLen = readVarInt();
        if (MAX_INVENTORY_ITEMS < arrayLen)
            throw new ProtocolException("Too many items in INV message: " + arrayLen);
        length = (int)(cursor - offset + (arrayLen * InventoryItem.MESSAGE_LENGTH));

        // An inv is vector<CInv> where CInv is int+hash.  The int is either 1 or 2 for tx or block.
        items = new ArrayList<>((int)arrayLen);
        for (int i = 0; i < arrayLen; i++)
        {
            if (payload.length < cursor + InventoryItem.MESSAGE_LENGTH)
                throw new ProtocolException("Ran off the end of the INV");

            int typeCode = (int)readUint32();
            InventoryItem.Type type;
            // see ppszTypeName in net.h
            switch (typeCode)
            {
                case 0:
                    type = InventoryItem.Type.Error;
                    break;
                case 1:
                    type = InventoryItem.Type.Transaction;
                    break;
                case 2:
                    type = InventoryItem.Type.Block;
                    break;
                case 3:
                    type = InventoryItem.Type.FilteredBlock;
                    break;
                default:
                    throw new ProtocolException("Unknown CInv type: " + typeCode);
            }
            items.add(new InventoryItem(type, readHash()));
        }
        payload = null;
    }

    @Override
    public void bitcoinSerializeToStream(OutputStream stream)
        throws IOException
    {
        stream.write(new VarInt(items.size()).encode());
        for (InventoryItem i : items)
        {
            // Write out the type code.
            Utils.uint32ToByteStreamLE(i.type.ordinal(), stream);
            // And now the hash.
            stream.write(i.hash.getReversedBytes());
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return items.equals(((ListMessage)o).items);
    }

    @Override
    public int hashCode()
    {
        return items.hashCode();
    }
}
