package io.kaleido.samples.nonce.flows;


import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import io.kaleido.samples.nonce.contracts.NonceContract;
import io.kaleido.samples.nonce.states.DummyNonceState;
import io.kaleido.samples.nonce.states.GroupNonce;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.PageSpecification;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.Sort;
import net.corda.core.node.services.vault.SortAttribute;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.util.*;
import java.util.stream.Collectors;

import static net.corda.core.node.services.vault.QueryCriteriaUtils.DEFAULT_PAGE_NUM;

@StartableByRPC
@InitiatingFlow
public class CreateDummyNonceState extends FlowLogic<SignedTransaction> {
    private final List<Party> observers;
    private final UUID groupId;
    private final String message;
    private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on dummy nonce state.");
    private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
    private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
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
            FINALISING_TRANSACTION
    );

    public CreateDummyNonceState(UUID groupId, String message, List<Party> observers) {
        this.observers = observers;
        this.groupId = groupId;
        this.message = message;
    }

    public GroupNonce updateGroupNonce(GroupNonce oldNonce) {
        return new GroupNonce(oldNonce.getLinearId(), new LinkedHashSet<>(oldNonce.getParticipants()), oldNonce.getNonce()+1);
    }

    @Suspendable
    private StateAndRef<GroupNonce> getGroupNonce(UUID groupId) throws FlowException {
        Set<AbstractParty> partiesInContext = new LinkedHashSet<>(this.observers);
        partiesInContext.add(getOurIdentity());
        UniqueIdentifier linearId = new UniqueIdentifier(null, groupId);
        QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(
                null,
                ImmutableList.of(linearId),
                Vault.StateStatus.UNCONSUMED,
                null);
        Sort.SortColumn oldestFirst = new Sort.SortColumn(new SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME), Sort.Direction.ASC);
        Vault.Page<GroupNonce> res = getServiceHub().getVaultService().queryBy(GroupNonce.class, queryCriteria, new PageSpecification(DEFAULT_PAGE_NUM, 1), new Sort(ImmutableList.of(oldestFirst)));
        if (res.getStates().isEmpty()) {
            return subFlow(new CreateGroupNonce(linearId, partiesInContext));
        } else {
            return res.getStates().get(0);
        }
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        // Obtain a reference to the notary we want to use.
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        // Generate an unsigned transaction.
        progressTracker.setCurrentStep(GENERATING_TRANSACTION);
        Party me = getOurIdentity();
        List<Party> participants = new ArrayList<>(observers);
        participants.add(me);
        final Command<NonceContract.Commands.NonceConsume> txCommand = new Command<>(
                new NonceContract.Commands.NonceConsume(),
                ImmutableList.of(me.getOwningKey()));
        final StateAndRef<GroupNonce> inContext = getGroupNonce(groupId);
        final DummyNonceState output = new DummyNonceState(getOurIdentity(), message, participants);
        final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .addInputState(inContext)
                .addOutputState(output, NonceContract.ID)
                .addOutputState(updateGroupNonce(inContext.getState().getData()), NonceContract.ID)
                .addCommand(txCommand);
        progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
        txBuilder.verify(getServiceHub());

        progressTracker.setCurrentStep(SIGNING_TRANSACTION);
        final SignedTransaction signedTx = getServiceHub().signInitialTransaction(txBuilder);

        progressTracker.setCurrentStep(FINALISING_TRANSACTION);
        Set<FlowSession> flowSessions = observers.stream().map(this::initiateFlow).collect(Collectors.toSet());
        return subFlow(new FinalityFlow(signedTx, flowSessions));
    }
}
