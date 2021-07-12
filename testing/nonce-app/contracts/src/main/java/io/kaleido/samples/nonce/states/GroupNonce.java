package io.kaleido.samples.nonce.states;

import io.kaleido.samples.nonce.contracts.NonceContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@BelongsToContract(NonceContract.class)
public class GroupNonce implements LinearState {
    private final UniqueIdentifier groupId;
    private final Set<AbstractParty> partiesForContext;
    private final long nonce;

    public GroupNonce(UniqueIdentifier groupId, Set<AbstractParty> partiesForContext, long nonce) {
        this.groupId = groupId;
        this.partiesForContext = partiesForContext;
        this.nonce = nonce;
    }

    public long getNonce() {
        return this.nonce;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return this.groupId;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return new ArrayList<>(partiesForContext);
    }
}
