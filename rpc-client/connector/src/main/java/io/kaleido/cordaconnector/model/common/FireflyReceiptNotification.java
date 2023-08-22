package io.kaleido.cordaconnector.model.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import net.corda.core.contracts.StateRef;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.transactions.CoreTransaction;
import net.corda.core.transactions.SignedTransaction;

public class FireflyReceiptNotification {
  private FireflyReceiptHeaders headers;
  private String transactionHash;
  private String errorMessage;
  private String protocolID;
  private Map<String, String> contractLocation;

  public FireflyReceiptNotification() {
  }

  public FireflyReceiptHeaders getHeaders() {
    return headers;
  }

  public void setHeaders(FireflyReceiptHeaders headers) {
    this.headers = headers;
  }

  public String getTransactionHash() {
    return transactionHash;
  }

  public void setTransactionHash(String transactionHash) {
    this.transactionHash = transactionHash;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getProtocolID() {
    return protocolID;
  }

  public void setProtocolID(String protocolID) {
    this.protocolID = protocolID;
  }

  public Map<String, String> getContractLocation() {
    return contractLocation;
  }

  public void setContractLocation(Map<String, String> contractLocation) {
    this.contractLocation = contractLocation;
  }

  public static FireflyReceiptNotification build(String requestId, SignedTransaction signedTransaction,
      CordaRPCOps rpcOps) {
    FireflyReceiptNotification notification = new FireflyReceiptNotification();
    FireflyReceiptHeaders headers = new FireflyReceiptHeaders();
    headers.setRequestId(requestId);
    headers.setType("TransactionSuccess");
    notification.setHeaders(headers);
    CoreTransaction coreTransaction = signedTransaction.getCoreTransaction();
    notification.setTransactionHash(coreTransaction.getId().toString());
    notification.setProtocolID(coreTransaction.getId().toString());

    List<String> inputTypes = getInputTypesFromTx(coreTransaction, rpcOps);
    List<String> outputTypes = getOutputTypesFromTx(coreTransaction);
    Map<String, String> contractLocation = new java.util.HashMap<>();

    int counter = 0;
    for (String inputType : inputTypes) {
      String key = String.format("input-%d", counter++);
      contractLocation.put(key, inputType);
    }
    counter = 0;
    for (String outputType : outputTypes) {
      String key = String.format("output-%d", counter++);
      contractLocation.put(key, outputType);
    }
    notification.setContractLocation(contractLocation);

    return notification;
  }

  @SuppressWarnings("deprecation")
  private static List<String> getInputTypesFromTx(CoreTransaction coreTransaction, CordaRPCOps rpcOps) {
    List<String> inputList = new ArrayList<>();
    for (StateRef stateRef : coreTransaction.getInputs()) {
      SignedTransaction signedTransaction = null;
      signedTransaction = rpcOps.internalFindVerifiedTransaction(stateRef.getTxhash());
      if (signedTransaction != null) {
        inputList.add(signedTransaction.getCoreTransaction().getOutputStates().get(stateRef.getIndex())
            .getClass().getCanonicalName());
      }
    }
    return inputList;
  }

  private static List<String> getOutputTypesFromTx(CoreTransaction coreTransaction) {
    List<String> outputList = new ArrayList<>();
    coreTransaction.getOutputStates().forEach(contractState -> {
      outputList.add(contractState.getClass().getCanonicalName());
    });
    return outputList;
  }
}
