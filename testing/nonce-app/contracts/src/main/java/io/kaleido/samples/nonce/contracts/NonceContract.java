package io.kaleido.samples.nonce.contracts;

import io.kaleido.samples.nonce.states.DummyNonceState;
import io.kaleido.samples.nonce.states.GroupNonce;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

public class NonceContract implements Contract {
    public static final String ID = "io.kaleido.samples.nonce.contracts.NonceContract";

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        final Commands commandData = command.getValue();
        final Set<PublicKey> setOfSigners = new HashSet<>(command.getSigners());
        if(commandData instanceof Commands.NonceConsume) {
            verifyNonceConsume(tx, setOfSigners);
        } else if(commandData instanceof Commands.NonceCreate) {
            verifyNonceCreate(tx, setOfSigners);
        } else {
            throw new IllegalArgumentException("Unrecognised command.");
        }
    }

    private void verifyNonceCreate(LedgerTransaction tx, Set<PublicKey> setOfSigners) {
        requireThat(require -> {
            List<GroupNonce> outContexts = tx.outputsOfType(GroupNonce.class);
            require.using("No inputs should be consumed when creating an group nonce between parties.",
                    tx.getInputs().isEmpty());
            require.using("Only one output nonce should be created.",
                    tx.getOutputs().size() == 1 && outContexts.size() == 1);
            final GroupNonce out = outContexts.get(0);
            final List<PublicKey> keys = out.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList());
            require.using("All of the participants must be signers.",
                    setOfSigners.containsAll(keys));
            require.using("The nonce value must be 0.",
                    out.getNonce() == 0L);
            return null;
        });
    }

    private void verifyNonceConsume(LedgerTransaction tx, Set<PublicKey> setOfSigners) {
        requireThat(require -> {
            List<GroupNonce> consumedNonces = tx.inputsOfType(GroupNonce.class);
            List<GroupNonce> unconsumedNonces = tx.outputsOfType(GroupNonce.class);
            List<DummyNonceState> dummyNonceStates = tx.outputsOfType(DummyNonceState.class);
            require.using("A single group nonce must be consumed when creating dummy nonce state.",
                    tx.getInputs().size() == 1 && consumedNonces.size() == 1);
            require.using("One dummy nonce  state and a new group nonce be created.",
                    tx.getOutputs().size() == 2 && unconsumedNonces.size() == 1 && dummyNonceStates.size() == 1);
            final GroupNonce consumedNonce = consumedNonces.get(0);
            final GroupNonce outNonce = unconsumedNonces.get(0);
            final DummyNonceState outState = dummyNonceStates.get(0);
            require.using("The group nonce value must be incremented by 1.",
                    outNonce.getNonce() == consumedNonce.getNonce()+1);
            require.using("The output and input nonce groups must be same.",
                    outNonce.getLinearId().equals(consumedNonce.getLinearId()));
            require.using("participants of input group nonce should be same as output group nonce", consumedNonce.getParticipants().equals(outNonce.getParticipants()));
            //require.using("participants of nonce group should be same as output dummy state", consumedNonce.getParticipants().equals(outState.getParticipants()));
            require.using("author must be a signer", setOfSigners.contains(outState.getAuthor().getOwningKey()));
            return null;
        });
    }

    public interface Commands extends CommandData {
        class NonceCreate implements Commands {}
        class NonceConsume implements Commands {}
    }
}
