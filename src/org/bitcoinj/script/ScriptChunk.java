package org.bitcoinj.script;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import javax.annotation.Nullable;

import com.google.common.base.Objects;
import static com.google.common.base.Preconditions.checkState;

import org.bitcoinj.core.Utils;
import static org.bitcoinj.script.ScriptOpCodes.*;

/**
 * A script element that is either a data push (signature, pubkey, etc.) or a non-push (logic, numeric, etc.) operation.
 */
public class ScriptChunk
{
    /** Operation to be executed.  Opcodes are defined in {@link ScriptOpCodes}. */
    public final int opcode;
    /**
     * For push operations, this is the vector to be pushed on the stack.
     * For {@link ScriptOpCodes#OP_0}, the vector is empty.
     * Null for non-push operations.
     */
    @Nullable
    public final byte[] data;
    private int startLocationInProgram;

    public ScriptChunk(int opcode, byte[] data)
    {
        this(opcode, data, -1);
    }

    public ScriptChunk(int opcode, byte[] data, int startLocationInProgram)
    {
        this.opcode = opcode;
        this.data = data;
        this.startLocationInProgram = startLocationInProgram;
    }

    public boolean equalsOpCode(int opcode)
    {
        return (opcode == this.opcode);
    }

    /**
     * If this chunk is a single byte of non-pushdata content (could be OP_RESERVED or some invalid Opcode).
     */
    public boolean isOpCode()
    {
        return (OP_PUSHDATA4 < opcode);
    }

    /**
     * Returns true if this chunk is pushdata content, including the single-byte pushdatas.
     */
    public boolean isPushData()
    {
        return (opcode <= OP_16);
    }

    public int getStartLocationInProgram()
    {
        checkState(0 <= startLocationInProgram);

        return startLocationInProgram;
    }

    /** If this chunk is an OP_N opcode returns the equivalent integer value. */
    public int decodeOpN()
    {
        checkState(isOpCode());

        return Script.decodeFromOpN(opcode);
    }

    /**
     * Called on a pushdata chunk, returns true if it uses the smallest possible way (according to BIP62) to push the data.
     */
    public boolean isShortestPossiblePushData()
    {
        checkState(isPushData());

        if (data == null)
            return true; // OP_N
        if (data.length == 0)
            return opcode == OP_0;
        if (data.length == 1)
        {
            byte b = data[0];
            if (0x01 <= b && b <= 0x10)
                return (opcode == OP_1 + b - 1);
            if ((b & 0xFF) == 0x81)
                return (opcode == OP_1NEGATE);
        }
        if (data.length < OP_PUSHDATA1)
            return opcode == data.length;
        if (data.length < 256)
            return opcode == OP_PUSHDATA1;
        if (data.length < 65536)
            return opcode == OP_PUSHDATA2;

        // Can never be used, but implemented for completeness.
        return (opcode == OP_PUSHDATA4);
    }

    public void write(OutputStream stream)
        throws IOException
    {
        if (isOpCode())
        {
            checkState(data == null);
            stream.write(opcode);
        }
        else if (data != null)
        {
            if (opcode < OP_PUSHDATA1)
            {
                checkState(data.length == opcode);
                stream.write(opcode);
            }
            else if (opcode == OP_PUSHDATA1)
            {
                checkState(data.length <= 0xff);
                stream.write(OP_PUSHDATA1);
                stream.write(data.length);
            }
            else if (opcode == OP_PUSHDATA2)
            {
                checkState(data.length <= 0xffff);
                stream.write(OP_PUSHDATA2);
                stream.write(0xff & data.length);
                stream.write(0xff & (data.length >> 8));
            }
            else if (opcode == OP_PUSHDATA4)
            {
                checkState(data.length <= Script.MAX_SCRIPT_ELEMENT_SIZE);
                stream.write(OP_PUSHDATA4);
                Utils.uint32ToByteStreamLE(data.length, stream);
            }
            else
            {
                throw new RuntimeException("Unimplemented");
            }
            stream.write(data);
        }
        else
        {
            stream.write(opcode); // smallNum
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (isOpCode())
            sb.append(getOpCodeName(opcode));
        else if (data != null) // Data chunk.
            sb.append(getPushDataName(opcode)).append("[").append(Utils.HEX.encode(data)).append("]");
        else // Small num.
            sb.append(Script.decodeFromOpN(opcode));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ScriptChunk other = (ScriptChunk)o;
        return (opcode == other.opcode && startLocationInProgram == other.startLocationInProgram && Arrays.equals(data, other.data));
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(opcode, startLocationInProgram, Arrays.hashCode(data));
    }
}
