package org.bitcoinj.core;

import org.bitcoinj.script.ScriptError;

@SuppressWarnings("serial")
public class ScriptException extends VerificationException {

    private final ScriptError err;

    public ScriptException(ScriptError err, String msg) {
        super(msg);
        this.err = err;
    }

    public ScriptException(ScriptError err, String msg, Exception e) {
        super(msg, e);
        this.err = err;
    }

    public ScriptError getError() {
        return err;
    }
}
