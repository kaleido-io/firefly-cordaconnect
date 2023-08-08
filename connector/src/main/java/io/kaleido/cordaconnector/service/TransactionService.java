package io.kaleido.cordaconnector.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.kaleido.cordaconnector.exception.CordaConnectionException;
import io.kaleido.cordaconnector.model.common.FlowParamter;
import io.kaleido.cordaconnector.model.common.TransactionData;
import io.kaleido.cordaconnector.model.common.TransactionInfo;
import io.kaleido.cordaconnector.rpc.NodeRPCClient;
import io.kaleido.cordaconnector.ws.WebSocketConnection;
import net.corda.core.CordaRuntimeException;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.transactions.SignedTransaction;

@Service
public class TransactionService {
  private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

  @Autowired
  private NodeRPCClient rpcClient;
  @Autowired
  private ReceiptService receiptService;

  public TransactionInfo createTransaction(TransactionData data) {
    logger.info("createTransaction: {}", data);
    String flowClassType = data.getFlowInitiatorClass();

    Class<? extends FlowLogic<? extends SignedTransaction>> flowClass;
    try {
      flowClass = (Class<? extends FlowLogic<? extends SignedTransaction>>) Class
          .forName(flowClassType).asSubclass(FlowLogic.class);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      return null;
    }

    CordaFuture<SignedTransaction> future;

    CordaRPCOps rpcOps = this.getRPCOps();
    List<Object> params = this.deserializeParams(data.getParams(), flowClass);

    try {
      FlowHandle<SignedTransaction> flowHandle = null;
      try {
        flowHandle = rpcOps.startFlowDynamic(flowClass, params.toArray());
      } catch (final CordaRuntimeException cre) {
        logger.error("Failed to start the flow", cre);
        return null;
      }

      logger.info("Started flow, handle: {}", flowHandle.toString());
      future = flowHandle.getReturnValue();
    } catch (final Exception e) {
      logger.error("Failed", e);
      return null;
    }

    if (future != null) {
      try {
        final SignedTransaction result = future.get();
        logger.info("Signed tx: {}", result);
        TransactionInfo txInfo = TransactionInfo.build(result, this.getRPCOps());
        receiptService.broadcastReceipt(txInfo);
        return txInfo;
      } catch (final Exception e) {
        logger.error("Failed to get transaction state", e);
      }
    }

    return null;
  }

  public TransactionInfo getTransaction(String txId) {
    logger.info("getTransaction: {}", txId);
    CordaRPCOps rpcOps = this.getRPCOps();
    SecureHash txHash = SecureHash.parse(txId);
    SignedTransaction tx = rpcOps.internalFindVerifiedTransaction(txHash);
    TransactionInfo txInfo = TransactionInfo.build(tx, rpcOps);
    txInfo.setVerified(true);
    return txInfo;
  }

  private CordaRPCOps getRPCOps() {
    CordaRPCOps rpcOps;
    try {
      rpcOps = rpcClient.getRpcProxy();
      return rpcOps;
    } catch (CordaConnectionException e) {
      e.printStackTrace();
      return null;
    }
  }

  private List<Object> deserializeParams(List<FlowParamter> params,
      Class<? extends FlowLogic<? extends SignedTransaction>> initiatorClass) {
    Class<?>[] parameterTypes = initiatorClass.getConstructors()[0].getParameterTypes();
    List<Object> deserializedParams = new ArrayList<>();
    for (int i = 0; i < params.size(); i++) {
      FlowParamter param = params.get(i);
      deserializedParams.add(this.deserialize(param, parameterTypes[i]));
    }
    return deserializedParams;
  }

  private Object deserialize(FlowParamter param, Class<?> paramType) {
    String value = param.getValue();
    String type = paramType.getName();
    switch (type) {
      case "int":
        return Integer.parseInt(value);
      case "string":
        return value;
      case "net.corda.core.identity.Party":
        CordaRPCOps rpcOps = this.getRPCOps();
        return this.resolveParty(rpcOps, value);
      case "net.corda.core.contracts.UniqueIdentifier":
        return new UniqueIdentifier(param.getExternalId(), UUID.fromString(value));
      case "net.corda.core.crypto.SecureHash":
        return SecureHash.parse(value);
      case "PublicKey":
        throw new IllegalArgumentException("PublicKey not yet supported");
      case "Amount<Currency>":
        throw new IllegalArgumentException("Amount<Currency> not yet supported");
      default:
        throw new IllegalArgumentException("Invalid type " + type);
    }
  }

  private Party resolveParty(CordaRPCOps rpcOps, String party) {
    final Set<Party> parties = rpcOps.partiesFromName(party, false);
    Party resolvedParty = null;
    if (parties.size() < 1) {
      throw new IllegalArgumentException("Failed to find a network party that matches borrower");
    } else {
      logger.info("Found {} parties in the network", parties.size());
      final Iterator<Party> itr = parties.iterator();
      while (itr.hasNext()) {
        final Party b = itr.next();
        if (b.toString().contains(party)) {
          resolvedParty = b;
          logger.info("Resolved party paramter {} to {}", party, resolvedParty);
          break;
        }
      }
    }
    return resolvedParty;
  }
}
