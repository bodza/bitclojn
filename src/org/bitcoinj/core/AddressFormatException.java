package org.bitcoinj.core;

public class AddressFormatException extends IllegalArgumentException
{
    public AddressFormatException()
    {
        super();
    }

    public AddressFormatException(String message)
    {
        super(message);
    }
}
