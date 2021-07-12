package io.kaleido.samples.nonce.flows;

import co.paralleluniverse.fibers.Suspendable;
import io.kaleido.samples.nonce.contracts.NonceContract;
import io.kaleido.samples.nonce.states.GroupNonce;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@StartableByRPC
@InitiatingFlow
public class CreateGroupNonce extends FlowLogic<StateAndRef<GroupNonce>> {
    private final UniqueIdentifier groupId;
    private final Set<AbstractParty> partiesForContext;
    private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on group Id");
    private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
    private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
    private final ProgressTracker.Step COLLECTING_SIGNATURES = new ProgressTracker.Step("Collecting signatures from parties within ordering group.") {
        @Override
        public ProgressTracker childProgressTracker() {
            return CollectSignaturesFlow.Companion.tracker();
        }
    };
    private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
        @Override
        public ProgressTracker childProgressTracker() {
            return FinalityFlow.Companion.tracker();
        }
    };

    private final ProgressTracker progressTracker = new ProgressTracker(
            GENERATING_TRANSACTION,
            VERIFYING_TRANSACTION,
            SIGNING_TRANSACTION,
            COLLECTING_SIGNATURES,
            FINALISING_TRANSACTION
    );

    public CreateGroupNonce(UniqueIdentifier groupId, Set<AbstractParty> partiesForContext) {
        this.groupId = groupId;
        this.partiesForContext = partiesForContext;
    }

    @Override
    @Suspendable
    public StateAndRef<GroupNonce> call() throws FlowException {
        // Obtain a reference to the notary we want to use.
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        // Generate an unsigned transaction.
        progressTracker.setCurrentStep(GENERATING_TRANSACTION);
        final List<PublicKey> signers = partiesForContext.stream().map(AbstractParty::getOwningKey).collect(Collectors.toList());
        final Command<NonceContract.Commands.NonceCreate> txCommand = new Command<>(
                new NonceContract.Commands.NonceCreate(),
                signers);
        final GroupNonce groupNonce = new GroupNonce(groupId, partiesForContext, 0L);

        final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .addOutputState(groupNonce, NonceContract.ID)
                .addCommand(txCommand);
        progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
        txBuilder.verify(getServiceHub());

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
        final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        progressTracker.setCurrentStep(COLLECTING_SIGNATURES);
        Set<FlowSession> flowSessions = partiesForContext.stream().filter(party -> !party.getOwningKey().equals(getOurIdentity().getOwningKey())).map(this::initiateFlow).collect(Collectors.toSet());
        SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(signedTx, flowSessions, COLLECTING_SIGNATURES.childProgressTracker()));
        progressTracker.setCurrentStep(FINALISING_TRANSACTION);
        SignedTransaction confirmedTx = subFlow(new FinalityFlow(fullySignedTx, flowSessions, FINALISING_TRANSACTION.childProgressTracker()));
        return confirmedTx.getTx().outRef(0);
    }
}
