package org.bitcoinj.core;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Thrown to indicate that you don't have enough money available to perform the requested operation.
 */
public class InsufficientMoneyException extends Exception {
    /** Contains the number of satoshis that would have been required to complete the operation. */
    @Nullable
    public final Coin missing;

    protected InsufficientMoneyException() {
        this.missing = null;
    }

    public InsufficientMoneyException(Coin missing) {
        this(missing, "Insufficient money,  missing " + missing.toFriendlyString());
    }

    public InsufficientMoneyException(Coin missing, String message) {
        super(message);
        this.missing = checkNotNull(missing);
    }
}
