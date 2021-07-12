package io.kaleido.samples.nonce.flows;

import co.paralleluniverse.fibers.Suspendable;
import io.kaleido.samples.nonce.states.GroupNonce;
import net.corda.core.contracts.ContractState;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

import static net.corda.core.contracts.ContractsDSL.requireThat;

@InitiatedBy(CreateGroupNonce.class)
public class ReceiveGroupNonce extends FlowLogic<SignedTransaction> {
    private final FlowSession otherPartySession;

    public ReceiveGroupNonce(FlowSession otherPartySession) {
        this.otherPartySession = otherPartySession;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        class SignTxFlow extends SignTransactionFlow {
            private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                super(otherPartyFlow, progressTracker);
            }

            @Override
            protected void checkTransaction(SignedTransaction stx) {
                requireThat(require -> {
                    ContractState output = stx.getTx().getOutputs().get(0).getData();
                    require.using("This must be an group nonce creation transaction.", output instanceof GroupNonce);
                    return null;
                });
            }
        }
        final SignTxFlow signTxFlow = new SignTxFlow(otherPartySession, SignTransactionFlow.Companion.tracker());
        final SecureHash txId = subFlow(signTxFlow).getId();
        return subFlow(new ReceiveFinalityFlow(otherPartySession, txId));
    }
}
