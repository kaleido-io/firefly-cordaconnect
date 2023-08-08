package io.kaleido.cordaconnector.model.common;

public class FlowParamter {
  private String value; // for now, all values must be deserilizable from a String
  private String externalId; // only applicable to UniqueIdentifier

  public FlowParamter() {
  }

  public String getValue() {
    return this.value;
  }

  public String getExternalId() {
    return this.externalId;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public void setExternalId(String externalId) {
    this.externalId = externalId;
  }
}
