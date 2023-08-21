package io.kaleido.cordaconnector.model.common;

import java.util.List;

public class TransactionData {
  private String flowInitiatorClass;
  private List<FlowParamter> params;

  public TransactionData() {
  }

  public String getFlowInitiatorClass() {
    return this.flowInitiatorClass;
  }

  public List<FlowParamter> getParams() {
    return this.params;
  }

  public void setFlowInitiatorClass(String flowClass) {
    this.flowInitiatorClass = flowClass;
  }

  public void setParams(List<FlowParamter> flowParameters) {
    this.params = flowParameters;
  }
}
