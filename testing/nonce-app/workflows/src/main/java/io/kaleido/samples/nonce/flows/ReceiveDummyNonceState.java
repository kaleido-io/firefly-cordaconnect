package io.kaleido.samples.nonce.flows;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.node.StatesToRecord;

@InitiatedBy(CreateDummyNonceState.class)
public class ReceiveDummyNonceState extends FlowLogic<Void> {
    private final FlowSession otherPartySession;

    public ReceiveDummyNonceState(FlowSession otherPartySession) {
        this.otherPartySession = otherPartySession;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        subFlow(new ReceiveFinalityFlow(otherPartySession, null, StatesToRecord.ALL_VISIBLE));
        return null;
    }
}
