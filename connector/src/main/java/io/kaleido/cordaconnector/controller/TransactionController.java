package io.kaleido.cordaconnector.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.kaleido.cordaconnector.model.common.TransactionData;
import io.kaleido.cordaconnector.model.common.TransactionInfo;
import io.kaleido.cordaconnector.model.request.ConnectorRequest;
import io.kaleido.cordaconnector.model.response.ConnectorResponse;
import io.kaleido.cordaconnector.service.TransactionService;

@RestController
public class TransactionController {
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
}
