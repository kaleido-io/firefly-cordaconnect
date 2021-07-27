package io.kaleido.samples.nonce;


import io.kaleido.samples.nonce.flows.CreateDummyNonceState;
import io.kaleido.samples.nonce.flows.CreateGroupNonce;
import io.kaleido.samples.nonce.states.GroupNonce;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCConnection;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.NotaryException;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.utilities.NetworkHostAndPort;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class KaleidoBasedTest {
    private CordaRPCConnection node1Connection;
    private CordaRPCConnection node2Connection;
    private final NetworkHostAndPort node1Address = NetworkHostAndPort.parse(System.getProperty("integrationTest.node1.address"));
    private final NetworkHostAndPort node2Address = NetworkHostAndPort.parse(System.getProperty("integrationTest.node2.address"));
    private final CordaRPCClient node1Client = new CordaRPCClient(node1Address);
    private final CordaRPCClient node2Client =  new CordaRPCClient(node2Address);
    private final long MAX_RETRIES = 50;
    private final Random random = new Random();
    private AtomicInteger numFailures = new AtomicInteger(0);
    private int numThreads = Integer.parseInt(System.getProperty("integrationTest.concurrency"));
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(numThreads);

    @Before
    public void SetupConnection() {
        String node1User = System.getProperty("integrationTest.node1.username");
        String node1Pass = System.getProperty("integrationTest.node1.password");
        String node2User = System.getProperty("integrationTest.node2.username");
        String node2Pass = System.getProperty("integrationTest.node2.password");
        node1Connection = node1Client.start(node1User, node1Pass);
        node2Connection = node2Client.start(node2User, node2Pass);
    }

    @After
    public void clean() {
        node1Connection.notifyServerAndClose();
        node2Connection.notifyServerAndClose();
    }

    private void sendTransactionWithRetry(UUID groupId, CordaRPCOps proxy, Party counterParty){
        long numRetries = 0;
        long timeToWait = random.nextInt(1500);
        while(true) {
            try{
                proxy.startFlowDynamic(CreateDummyNonceState.class, groupId, "dummy message", Collections.singletonList(counterParty)).getReturnValue().get();
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
    public void reproduceFinalityFailuresOnKaleido() {
        try {
            // create nonce
            UUID groupId = UUID.randomUUID();
            CordaRPCOps node1Proxy = node1Connection.getProxy();
            CordaRPCOps node2Proxy = node2Connection.getProxy();

            Party partyA = node1Proxy.nodeInfo().getLegalIdentities().get(0);
            Party partyB = node2Proxy.nodeInfo().getLegalIdentities().get(0);
            Set<Party> parties = new LinkedHashSet<>();
            parties.add(partyA);
            parties.add(partyB);

            FlowHandle<StateAndRef<GroupNonce>> flowHandle = node1Connection.getProxy().startFlowDynamic(CreateGroupNonce.class, new UniqueIdentifier(null, groupId), parties);
            flowHandle.getReturnValue().get();
            int numberOfTxns = Integer.parseInt(System.getProperty("integrationTest.numberOfTransactions"));
            CountDownLatch latch = new CountDownLatch(numberOfTxns);
            for(int i=0; i<numberOfTxns/2; i++) {
                scheduler.submit(() -> {
                    sendTransactionWithRetry(groupId, node1Proxy, partyB);
                    latch.countDown();
                });
                scheduler.submit(() -> {
                    sendTransactionWithRetry(groupId, node2Proxy, partyA);
                    latch.countDown();
                });
            }
            latch.await();
            assertEquals(numFailures.get(), 0);
        } catch (Exception e) {
            throw new RuntimeException("Caught exception during test: ", e);
        }
    }
}
