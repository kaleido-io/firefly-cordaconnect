package io.kaleido.samples.nonce.states;

import io.kaleido.samples.nonce.contracts.NonceContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@BelongsToContract(NonceContract.class)
public class DummyNonceState implements ContractState {
    private final Party author;
    private final String message;
    private final List<Party> participants;

    public DummyNonceState(Party author, String message, List<Party> participants) {
        this.author = author;
        this.message = message;
        this.participants = participants;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return new ArrayList<>(participants);
    }

    @Override
    public String toString() {
        return String.format("DummyNonceState(author=%s, message=%s, participants=%s)", author, message, participants);
    }

    public Party getAuthor() {
        return author;
    }

    public String getMessage() {
        return message;
    }
}
