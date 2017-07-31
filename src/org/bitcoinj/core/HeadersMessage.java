package org.bitcoinj.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A protocol message that contains a repeated series of block headers, sent in response to the "getheaders" command.
 * This is useful when you want to traverse the chain but know you don't care about the block contents, for example,
 * because you have a freshly created wallet with no keys.</p>
 *
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public class HeadersMessage extends Message
{
    private static final Logger log = LoggerFactory.getLogger(HeadersMessage.class);

    // The main client will never send us more than this number of headers.
    public static final int MAX_HEADERS = 2000;

    private List<Block> blockHeaders;

    public HeadersMessage(NetworkParameters params, byte[] payload)
        throws ProtocolException
    {
        super(params, payload, 0);
    }

    public HeadersMessage(NetworkParameters params, Block... headers)
        throws ProtocolException
    {
        super(params);
        blockHeaders = Arrays.asList(headers);
    }

    public HeadersMessage(NetworkParameters params, List<Block> headers)
        throws ProtocolException
    {
        super(params);
        blockHeaders = headers;
    }

    @Override
    public void bitcoinSerializeToStream(OutputStream stream)
        throws IOException
    {
        stream.write(new VarInt(blockHeaders.size()).encode());
        for (Block header : blockHeaders)
        {
            header.cloneAsHeader().bitcoinSerializeToStream(stream);
            stream.write(0);
        }
    }

    @Override
    protected void parse()
        throws ProtocolException
    {
        long numHeaders = readVarInt();
        if (MAX_HEADERS < numHeaders)
            throw new ProtocolException("Too many headers: got " + numHeaders + " which is larger than " + MAX_HEADERS);

        blockHeaders = new ArrayList<>();
        final BitcoinSerializer serializer = this.params.getSerializer(true);

        for (int i = 0; i < numHeaders; ++i)
        {
            final Block newBlockHeader = serializer.makeBlock(payload, cursor, UNKNOWN_LENGTH);
            if (newBlockHeader.hasTransactions())
                throw new ProtocolException("Block header does not end with a null byte");

            cursor += newBlockHeader.optimalEncodingMessageSize;
            blockHeaders.add(newBlockHeader);
        }

        if (length == UNKNOWN_LENGTH)
            length = cursor - offset;

        if (log.isDebugEnabled())
            for (int i = 0; i < numHeaders; ++i)
                log.debug(this.blockHeaders.get(i).toString());
    }

    public List<Block> getBlockHeaders()
    {
        return blockHeaders;
    }
}
