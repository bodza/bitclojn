package org.bitcoinj.signers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.wallet.KeyBag;
import org.bitcoinj.wallet.Wallet;

/**
 * This transaction signer resolves missing signatures in accordance with the given {@link org.bitcoinj.wallet.Wallet.MissingSigsMode}.
 * If missingSigsMode is USE_OP_ZERO this signer does nothing assuming missing signatures are already presented in scriptSigs as OP_0.
 * In MissingSigsMode.THROW mode this signer will throw an exception.  It would be MissingSignatureException
 * for P2SH or MissingPrivateKeyException for other transaction types.
 */
public class MissingSigResolutionSigner extends StatelessTransactionSigner
{
    private static final Logger log = LoggerFactory.getLogger(MissingSigResolutionSigner.class);

    public Wallet.MissingSigsMode missingSigsMode = Wallet.MissingSigsMode.USE_DUMMY_SIG;

    public MissingSigResolutionSigner()
    {
    }

    public MissingSigResolutionSigner(Wallet.MissingSigsMode missingSigsMode)
    {
        this.missingSigsMode = missingSigsMode;
    }

    @Override
    public boolean isReady()
    {
        return true;
    }

    @Override
    public boolean signInputs(ProposedTransaction propTx, KeyBag keyBag)
    {
        if (missingSigsMode == Wallet.MissingSigsMode.USE_OP_ZERO)
            return true;

        int numInputs = propTx.partialTx.getInputs().size();
        byte[] dummySig = TransactionSignature.dummy().encodeToBitcoin();
        for (int i = 0; i < numInputs; i++)
        {
            TransactionInput txIn = propTx.partialTx.getInput(i);
            if (txIn.getConnectedOutput() == null)
            {
                log.warn("Missing connected output, assuming input {} is already signed.", i);
                continue;
            }

            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
            Script inputScript = txIn.getScriptSig();
            if (scriptPubKey.isPayToScriptHash() || scriptPubKey.isSentToMultiSig())
            {
                int sigSuffixCount = scriptPubKey.isPayToScriptHash() ? 1 : 0;
                // All chunks except the first one (OP_0) and the last (redeem script) are signatures.
                for (int j = 1; j < inputScript.getChunks().size() - sigSuffixCount; j++)
                {
                    ScriptChunk scriptChunk = inputScript.getChunks().get(j);
                    if (scriptChunk.equalsOpCode(0))
                    {
                        if (missingSigsMode == Wallet.MissingSigsMode.THROW)
                            throw new MissingSignatureException();

                        if (missingSigsMode == Wallet.MissingSigsMode.USE_DUMMY_SIG)
                            txIn.setScriptSig(scriptPubKey.getScriptSigWithSignature(inputScript, dummySig, j - 1));
                    }
                }
            }
            else
            {
                if (inputScript.getChunks().get(0).equalsOpCode(0))
                {
                    if (missingSigsMode == Wallet.MissingSigsMode.THROW)
                        throw new ECKey.MissingPrivateKeyException();

                    if (missingSigsMode == Wallet.MissingSigsMode.USE_DUMMY_SIG)
                        txIn.setScriptSig(scriptPubKey.getScriptSigWithSignature(inputScript, dummySig, 0));
                }
            }
            // TODO: Handle non-P2SH multisig.
        }
        return true;
    }
}
