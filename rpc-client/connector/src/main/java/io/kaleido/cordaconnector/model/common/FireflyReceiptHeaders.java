package io.kaleido.cordaconnector.model.common;

public class FireflyReceiptHeaders {
  private String requestId;
  private String type;

  public FireflyReceiptHeaders() {
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}
