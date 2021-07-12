package io.kaleido.samples.nonce;

import com.google.common.collect.ImmutableList;
import io.kaleido.samples.nonce.flows.CreateDummyNonceState;
import io.kaleido.samples.nonce.flows.CreateGroupNonce;
import io.kaleido.samples.nonce.states.GroupNonce;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.NotaryException;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.node.NetworkParameters;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import net.corda.testing.node.TestCordapp;

import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


import static net.corda.testing.driver.Driver.driver;
import static org.junit.Assert.assertEquals;


public class DriverBasedTest {
    private final TestIdentity bankA = new TestIdentity(new CordaX500Name("BankA", "", "GB"));
    private final TestIdentity bankB = new TestIdentity(new CordaX500Name("BankB", "", "US"));
    private final List<TestCordapp> cordapps = Arrays.asList(TestCordapp.findCordapp("io.kaleido.samples.nonce.contracts"), TestCordapp.findCordapp("io.kaleido.samples.nonce.flows"));
    private final NetworkParameters defaultParams = new DriverParameters().getNetworkParameters();
    private final long MAX_RETRIES = 50;
    private final Random random = new Random();
    private AtomicInteger numFailures = new AtomicInteger(0);
    private int numThreads = Integer.parseInt(System.getProperty("integrationTest.concurrency"));
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(numThreads);
    private void sendTransactionWithRetry(UUID groupId, NodeHandle nodeHandle, Party counterParty){
        long numRetries = 0;
        long timeToWait = random.nextInt(1500);
        while(true) {
            try{
               nodeHandle.getRpc().startFlowDynamic(CreateDummyNonceState.class, groupId, "dummy message", Collections.singletonList(counterParty)).getReturnValue().get();
               return;
            } catch (ExecutionException | InterruptedException e) {
                if(e.getCause() instanceof NotaryException && numRetries < MAX_RETRIES) {
                    try {
                        Thread.sleep(timeToWait);
                    } catch (InterruptedException interruptedException) {
                        continue;
                    }
                    timeToWait += random.nextInt(1500);
                    numRetries++;
                    continue;
                } else {
                   numFailures.incrementAndGet();
                }
            }
        }
    }
    @Test
    public void reproduceFinalityFailures() {
        driver(new DriverParameters().withIsDebug(true).withStartNodesInProcess(true).withCordappsForAllNodes(cordapps).withNetworkParameters(new NetworkParameters(4, defaultParams.getNotaries(), defaultParams.getMaxMessageSize(), defaultParams.getMaxTransactionSize(), defaultParams.getModifiedTime(), defaultParams.getEpoch(), defaultParams.getWhitelistedContractImplementations(), defaultParams.getEventHorizon(), defaultParams.getPackageOwnership())), dsl -> {
            // Start a pair of nodes and wait for them both to be ready.
            List<CordaFuture<NodeHandle>> handleFutures = ImmutableList.of(
                    dsl.startNode(new NodeParameters().withProvidedName(bankA.getName())),
                    dsl.startNode(new NodeParameters().withProvidedName(bankB.getName()))
            );

            try {
                NodeHandle partyAHandle = handleFutures.get(0).get();
                NodeHandle partyBHandle = handleFutures.get(1).get();

                Party partyA = partyAHandle.getNodeInfo().getLegalIdentities().get(0);
                Party partyB = partyBHandle.getNodeInfo().getLegalIdentities().get(0);
                Set<Party> parties = new LinkedHashSet<>();
                parties.add(partyA);
                parties.add(partyB);

                // From each node, make an RPC call to retrieve another node's name from the network map, to verify that the
                // nodes have started and can communicate.

                // This is a very basic test: in practice tests would be starting flows, and verifying the states in the vault
                // and other important metrics to ensure that your CorDapp is working as intended.
                assertEquals(partyAHandle.getRpc().wellKnownPartyFromX500Name(bankB.getName()).getName(), bankB.getName());
                assertEquals(partyBHandle.getRpc().wellKnownPartyFromX500Name(bankA.getName()).getName(), bankA.getName());

                // create nonce
                UUID groupId = UUID.randomUUID();
                FlowHandle<StateAndRef<GroupNonce>> flowHandle = partyAHandle.getRpc().startFlowDynamic(CreateGroupNonce.class, new UniqueIdentifier(null, groupId), parties);
                flowHandle.getReturnValue().get();
                int numberOfTxns = Integer.parseInt(System.getProperty("integrationTest.numberOfTransactions"));
                CountDownLatch latch = new CountDownLatch(numberOfTxns);
                for(int i=0; i<numberOfTxns/2; i++) {
                    scheduler.submit(() -> {
                        sendTransactionWithRetry(groupId, partyAHandle, partyB);
                        latch.countDown();
                    });
                    scheduler.submit(() -> {
                        sendTransactionWithRetry(groupId, partyBHandle, partyA);
                        latch.countDown();
                    });
                }
                latch.await();
                assertEquals(numFailures.get(), 0);
            } catch (Exception e) {
                throw new RuntimeException("Caught exception during test: ", e);
            }

            return null;
        });
    }
}