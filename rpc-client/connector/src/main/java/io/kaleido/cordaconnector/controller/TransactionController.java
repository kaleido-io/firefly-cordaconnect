package io.kaleido.cordaconnector.controller;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.kaleido.cordaconnector.exception.CordaConnectionException;
import io.kaleido.cordaconnector.model.common.TransactionData;
import io.kaleido.cordaconnector.model.common.TransactionInfo;
import io.kaleido.cordaconnector.model.request.ConnectorRequest;
import io.kaleido.cordaconnector.model.response.ConnectorResponse;
import io.kaleido.cordaconnector.service.TransactionService;
import net.corda.core.contracts.ContractState;
import net.corda.core.node.services.Vault;

@RestController
public class TransactionController {
  private Logger logger = LoggerFactory.getLogger("TransactionController");

  @Autowired
  private TransactionService transactionService;

  @PostMapping("/transactions")
  public ConnectorResponse<TransactionInfo> createTransaction(@RequestBody ConnectorRequest<TransactionData> request) {
    TransactionInfo res = transactionService.createTransaction(request.getData());
    return new ConnectorResponse<>(res);
  }

  @GetMapping("/transactions/{txHash}")
  public ConnectorResponse<TransactionInfo> getTransaction(@PathVariable("txHash") String txHash) {
    TransactionInfo res = transactionService.getTransaction(txHash);
    return new ConnectorResponse<>(res);
  }

  @GetMapping("/states")
  public Vault.Page<ContractState> getStates(
      @RequestParam(value = "pageNumber", required = false) Integer pageNumber,
      @RequestParam(value = "pageSize", required = false) Integer pageSize,
      @RequestParam(value = "types", required = false) List<String> types,
      @RequestParam(value = "participants", required = false) List<String> participants,
      @RequestParam(value = "externalIds", required = false) List<String> externalIds,
      @RequestParam(value = "stateRefs", required = false) List<String> stateRefs,
      @RequestParam(value = "consumed", required = false) Boolean consumed,
      @RequestParam(value = "relevant", required = false) Boolean relevant,
      @RequestParam(value = "sort", required = false) String sortDirection)
      throws IOException, CordaConnectionException {
    logger.info("service object {}", transactionService);
    Vault.Page<ContractState> res = transactionService.getStates(types, participants, externalIds, stateRefs, consumed,
        relevant, pageNumber, pageSize, sortDirection);
    return res;
  }
}
