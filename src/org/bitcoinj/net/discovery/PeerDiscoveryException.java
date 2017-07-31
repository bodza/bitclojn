package org.bitcoinj.net.discovery;

public class PeerDiscoveryException extends Exception
{
    public PeerDiscoveryException()
    {
        super();
    }

    public PeerDiscoveryException(String message)
    {
        super(message);
    }

    public PeerDiscoveryException(Throwable arg0)
    {
        super(arg0);
    }

    public PeerDiscoveryException(String message, Throwable arg0)
    {
        super(message, arg0);
    }
}
