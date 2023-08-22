package io.kaleido.cordaconnector.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.collect.ImmutableSet;

import io.kaleido.cordaconnector.exception.CordaConnectionException;
import io.kaleido.cordaconnector.model.common.FireflyReceiptNotification;
import io.kaleido.cordaconnector.model.common.FlowParamter;
import io.kaleido.cordaconnector.model.common.TransactionData;
import io.kaleido.cordaconnector.model.common.TransactionInfo;
import io.kaleido.cordaconnector.rpc.NodeRPCClient;
import io.kaleido.cordaconnector.ws.WebSocketConnection;
import net.corda.core.CordaRuntimeException;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.FungibleState;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.OwnableState;
import net.corda.core.contracts.SchedulableState;
import net.corda.core.contracts.StateRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.FlowLogic;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.FlowHandle;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.PageSpecification;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.Sort;
import net.corda.core.node.services.vault.SortAttribute;
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria;
import net.corda.core.schemas.QueryableState;
import net.corda.core.transactions.SignedTransaction;

@Service
public class TransactionService {
  private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
  private static Map<String, Class<? extends ContractState>> nativeStateTypeMap = new HashMap<String, Class<? extends ContractState>>() {
    {
      put("linear", LinearState.class);
      put("ownable", OwnableState.class);
      put("schedulable", SchedulableState.class);
      put("fungible", FungibleState.class);
      put("queryable", QueryableState.class);
    }
  };

  private static Map<String, Class<? extends ContractState>> vaultStateTypeMap = new HashMap<>();
  static Pattern STATE_REF_VALIDATOR = Pattern.compile("^([A-F0-9]{64})\\(([0-9]+)\\)$");

  @Autowired
  private NodeRPCClient rpcClient;
  @Autowired
  private ReceiptService receiptService;

  public TransactionInfo createTransaction(String requestId, TransactionData data) {
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
        FireflyReceiptNotification receipt = FireflyReceiptNotification.build(requestId, result, this.getRPCOps());
        receiptService.broadcastReceipt(receipt);
        TransactionInfo txInfo = TransactionInfo.build(result, this.getRPCOps());
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

  public Vault.Page<ContractState> getStates(List<String> types, List<String> participants, List<String> externalIds,
      List<String> stateRefs, Boolean consumed, Boolean relevant, Integer pageNumber, Integer pageSize,
      String sortDirection)
      throws IOException, CordaConnectionException {
    CordaRPCOps rpcOps = this.getRPCOps();
    VaultQueryCriteria query = toVaultQueryCriteria(types, participants, externalIds, stateRefs, consumed, relevant);
    if (pageNumber == null)
      pageNumber = 0;
    if (pageSize == null)
      pageSize = 100;
    PageSpecification paging = new PageSpecification(pageNumber + 1/* Pages start from 1 */, pageSize);
    List<Sort.SortColumn> sortList = new ArrayList<Sort.SortColumn>();
    if (sortDirection != null) {
      Sort.Direction direction;
      if (sortDirection.equals("desc"))
        direction = Sort.Direction.DESC;
      else
        direction = Sort.Direction.ASC;
      Sort.SortColumn sortColumn = new Sort.SortColumn(
          new SortAttribute.Standard(Sort.VaultStateAttribute.RECORDED_TIME),
          direction);
      sortList.add(sortColumn);
    }

    return rpcOps.vaultQueryBy(query, paging, new Sort(sortList), ContractState.class);
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
      case "java.lang.String":
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

  private QueryCriteria.VaultQueryCriteria toVaultQueryCriteria(List<String> types, List<String> participants,
      List<String> externalIds, List<String> stateRefs, Boolean consumed, Boolean relevant)
      throws IOException, CordaConnectionException {

    Vault.StateStatus status = Vault.StateStatus.ALL;
    Vault.RelevancyStatus relevancyStatus = Vault.RelevancyStatus.ALL;
    Set<Class<? extends ContractState>> stateTypes = ImmutableSet.of(ContractState.class);
    HashSet<Class<? extends ContractState>> queryStateTypes = new HashSet<>();

    // filter for contractstates types if any
    if (types != null && types.size() > 0) {
      for (String type : types) {
        if (nativeStateTypeMap.containsKey(type)) {
          queryStateTypes.add(nativeStateTypeMap.get(type));
        } else if (vaultStateTypeMap.containsKey(type)) {
          queryStateTypes.add(vaultStateTypeMap.get(type));
        } else {
          throw new IllegalArgumentException("Unsupported State Type.");
        }
      }
    }

    if (queryStateTypes.size() > 0) {
      stateTypes = queryStateTypes;
    }
    logger.debug("Contract state filter types are {}",
        stateTypes.stream().map(contractStateClass -> contractStateClass.getName()).collect(Collectors.toList()));
    // filter for if state spent/unspent
    if (consumed != null) {
      if (consumed)
        status = Vault.StateStatus.CONSUMED;
      else
        status = Vault.StateStatus.UNCONSUMED;
    }

    // filter for if state relevant/not-relevant
    if (relevant != null) {
      if (relevant)
        relevancyStatus = Vault.RelevancyStatus.RELEVANT;
      else
        relevancyStatus = Vault.RelevancyStatus.NOT_RELEVANT;
    }

    // filter for externalIds if any
    List<UUID> externalIdList = new ArrayList<>();
    if (externalIds != null && externalIds.size() > 0) {
      for (String externalId : externalIds) {
        UUID uuid = UUID.fromString(externalId);
        externalIdList.add(uuid);
      }
    }

    // filter for participants involved if any
    Set<Party> parties = new HashSet<>();
    if (participants != null && participants.size() > 0) {
      for (String participant : participants) {
        CordaX500Name name = null;
        try {
          name = CordaX500Name.parse(participant);
        } catch (IllegalArgumentException ie) {
          logger.debug("{} is not a valid Corda x.500 name", participant);
          throw new IllegalArgumentException("Participant must be a valid Corda x.500 name.");
        }
        Party partyFromX500Name = getRPCOps().wellKnownPartyFromX500Name(name);
        if (partyFromX500Name != null) {
          parties.add(partyFromX500Name);
          logger.debug("Party exists for {}", participant);
        } else {
          logger.debug("No Party exists with legal name {}", participant);
          throw new IllegalArgumentException("No party exists for one of the participants.");
        }
      }
    }

    // filter for statesRefs if any
    List<StateRef> stateRefList = new ArrayList<>();
    if (stateRefs != null && stateRefs.size() > 0) {
      for (String stateRefStr : stateRefs) {
        Matcher matcher = STATE_REF_VALIDATOR.matcher(stateRefStr);
        if (!matcher.matches()) {
          throw new IllegalArgumentException("StateRef string is not well formed.");
        }
        String txHash = matcher.group(1);
        String index = matcher.group(2);
        stateRefList.add(new StateRef(SecureHash.parse(txHash), Integer.parseInt(index)));
      }
    }

    QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria()
        .withRelevancyStatus(relevancyStatus)
        .withContractStateTypes(stateTypes)
        .withStatus(status);

    if (stateRefList.size() > 0) {
      criteria = criteria.withStateRefs(stateRefList);
    }

    if (externalIdList.size() > 0) {
      criteria = addExternalIds(criteria, externalIdList);
    }

    if (parties.size() > 0) {
      List<Party> partyList = new ArrayList<>();
      partyList.addAll(parties);
      criteria = addParticipants(criteria, partyList);
    }

    return criteria;
  }

  private static QueryCriteria.VaultQueryCriteria addExternalIds(QueryCriteria.VaultQueryCriteria criteria,
      List<UUID> externalIds) {
    return new QueryCriteria.VaultQueryCriteria(
        criteria.getStatus(),
        criteria.getContractStateTypes(),
        criteria.getStateRefs(),
        criteria.getNotary(),
        criteria.getSoftLockingCondition(),
        criteria.getTimeCondition(),
        criteria.getRelevancyStatus(),
        criteria.getConstraintTypes(),
        criteria.getConstraints(),
        criteria.getParticipants(),
        externalIds);
  }

  private static QueryCriteria.VaultQueryCriteria addParticipants(QueryCriteria.VaultQueryCriteria criteria,
      List<Party> parties) {
    return new QueryCriteria.VaultQueryCriteria(
        criteria.getStatus(),
        criteria.getContractStateTypes(),
        criteria.getStateRefs(),
        criteria.getNotary(),
        criteria.getSoftLockingCondition(),
        criteria.getTimeCondition(),
        criteria.getRelevancyStatus(),
        criteria.getConstraintTypes(),
        criteria.getConstraints(),
        parties,
        criteria.getExternalIds());
  }
}
