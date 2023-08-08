package io.kaleido.cordaconnector.model.common;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateRef;
import net.corda.core.crypto.TransactionSignature;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.transactions.CoreTransaction;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.WireTransaction;

public class TransactionInfo {
  private List<StateAndType> inputs;
  private List<TypeCount> inputTypes;
  private List<StateAndType> outputs;
  private List<TypeCount> outputTypes;
  private List<String> commands;
  private List<Signer> signers;
  private String transactionId;
  private String notary;
  private Instant timestamp;
  private Boolean isVerified;

  public static class Signer {
    private TransactionSignature signature;
    private String partyName;

    public TransactionSignature getSignature() {
      return signature;
    }

    public void setSignature(TransactionSignature signature) {
      this.signature = signature;
    }

    public String getPartyName() {
      return partyName;
    }

    public void setPartyName(String partyName) {
      this.partyName = partyName;
    }
  }

  public static class TypeCount {
    private String type;
    private int count;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public int getCount() {
      return count;
    }

    public void setCount(int count) {
      this.count = count;
    }
  }

  public static class StateAndType {
    private ContractState state;
    private String type;
    private StateRef stateRef;

    public StateAndType(ContractState state, String type, StateRef stateRef) {
      this.state = state;
      this.type = type;
      this.stateRef = stateRef;
    }

    public ContractState getState() {
      return state;
    }

    public String getType() {
      return type;
    }

    public StateRef getStateRef() {
      return stateRef;
    }
  }

  public TransactionInfo() {
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public Boolean getVerified() {
    return isVerified;
  }

  public void setVerified(Boolean verified) {
    isVerified = verified;
  }

  public List<StateAndType> getInputs() {
    return inputs;
  }

  public void setInputs(List<StateAndType> inputs) {
    this.inputs = inputs;
  }

  public List<StateAndType> getOutputs() {
    return outputs;
  }

  public void setOutputs(List<StateAndType> outputs) {
    this.outputs = outputs;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public List<String> getCommands() {
    return commands;
  }

  public void setCommands(List<String> commands) {
    this.commands = commands;
  }

  public List<Signer> getSigners() {
    return signers;
  }

  public void setSigners(List<Signer> signers) {
    this.signers = signers;
  }

  public String getNotary() {
    return notary;
  }

  public void setNotary(String notary) {
    this.notary = notary;
  }

  public List<TypeCount> getInputTypes() {
    return inputTypes;
  }

  public void setInputTypes(List<TypeCount> inputTypes) {
    this.inputTypes = inputTypes;
  }

  public List<TypeCount> getOutputTypes() {
    return outputTypes;
  }

  public void setOutputTypes(List<TypeCount> outputTypes) {
    this.outputTypes = outputTypes;
  }

  public static TransactionInfo build(SignedTransaction signedTransaction, CordaRPCOps rpcOps) {
    TransactionInfo response = new TransactionInfo();
    CoreTransaction coreTransaction = signedTransaction.getCoreTransaction();

    response.setSigners(getSignersFromTx(signedTransaction, rpcOps));

    if (coreTransaction.getInputs().size() > 0) {
      response.setInputs(getInputsFromTx(coreTransaction, rpcOps));
      addInputTypeAndCount(response);
    }

    response.setOutputs(getOutputsFromTx(coreTransaction));
    response.setOutputTypes(getOutputTypeAndCount(coreTransaction));

    response.setTransactionId(coreTransaction.getId().toString());
    response.setCommands(((WireTransaction) coreTransaction).getCommands().stream()
        .map(command -> command.getValue().getClass().getCanonicalName().substring(command.getValue().getClass()
            .getCanonicalName().lastIndexOf(".") + 1))
        .collect(Collectors.toList()));

    response.setNotary(coreTransaction.getNotary().getName().getOrganisation());
    return response;
  }

  /*
   * List of utility function required to build transactionInfo object taken from
   * https://github.com/corda/node-server
   */
  private static List<Signer> getSignersFromTx(SignedTransaction signedTransaction, CordaRPCOps rpcOps) {
    List<Signer> signerList = new ArrayList<>();
    signedTransaction.getSigs().forEach(signature -> {
      // String key = CryptoUtils.toStringShort(signature.getBy());
      Signer signer = new Signer();
      signer.setSignature(signature);
      Party party = null;
      party = rpcOps.partyFromKey(signature.getBy());
      signer.setPartyName(party.getName().toString());
      signerList.add(signer);
    });
    return signerList;
  }

  @SuppressWarnings("deprecation")
  private static List<StateAndType> getInputsFromTx(CoreTransaction coreTransaction, CordaRPCOps rpcOps) {
    List<StateAndType> inputList = new ArrayList<>();
    for (StateRef stateRef : coreTransaction.getInputs()) {
      SignedTransaction signedTransaction = null;
      signedTransaction = rpcOps.internalFindVerifiedTransaction(stateRef.getTxhash());
      if (signedTransaction != null) {
        inputList.add(new StateAndType(
            signedTransaction.getCoreTransaction().getOutputStates().get(stateRef.getIndex()),
            signedTransaction.getCoreTransaction().getOutputStates().get(stateRef.getIndex())
                .getClass().getCanonicalName(),
            stateRef));
      }
    }
    return inputList;
  }

  private static List<StateAndType> getOutputsFromTx(CoreTransaction coreTransaction) {
    List<StateAndType> outputList = new ArrayList<>();
    AtomicReference<Integer> counter = new AtomicReference<>(0);
    coreTransaction.getOutputStates().forEach(contractState -> {
      outputList.add(new StateAndType(
          contractState,
          contractState.getClass().getCanonicalName(),
          new StateRef(coreTransaction.getId(), counter.get())));
      counter.getAndSet(counter.get() + 1);
    });
    return outputList;
  }

  private static void addInputTypeAndCount(TransactionInfo transactionData) {
    Map<String, Integer> inputTypeMap = new HashMap<>();
    transactionData.getInputs().forEach(stateAndType -> {
      String type = stateAndType.getState().getClass().toString().substring(
          stateAndType.getState().getClass().toString().lastIndexOf(".") + 1);
      if (inputTypeMap.containsKey(type)) {
        inputTypeMap.put(type, inputTypeMap.get(type) + 1);
      } else {
        inputTypeMap.put(type, 1);
      }
    });

    List<TypeCount> inputTypeCountList = new ArrayList<>();
    inputTypeMap.keySet().forEach(s -> {
      TypeCount typeCount = new TypeCount();
      typeCount.setType(s);
      typeCount.setCount(inputTypeMap.get(s));
      inputTypeCountList.add(typeCount);
    });

    transactionData.setInputTypes(inputTypeCountList);
  }

  private static List<TypeCount> getOutputTypeAndCount(CoreTransaction coreTransaction) {
    Map<String, Integer> outputTypeMap = new HashMap<>();
    coreTransaction.getOutputStates().forEach(contractState -> {
      String type = contractState.getClass().toString().substring(
          contractState.getClass().toString().lastIndexOf(".") + 1);
      if (outputTypeMap.containsKey(type)) {
        outputTypeMap.put(type, outputTypeMap.get(type) + 1);
      } else {
        outputTypeMap.put(type, 1);
      }
    });
    List<TypeCount> outputTypeCountList = new ArrayList<>();
    outputTypeMap.keySet().forEach(s -> {
      TypeCount typeCount = new TypeCount();
      typeCount.setType(s);
      typeCount.setCount(outputTypeMap.get(s));
      outputTypeCountList.add(typeCount);
    });
    return outputTypeCountList;
  }
}
