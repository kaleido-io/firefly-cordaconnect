package io.kaleido.cordaconnector.service;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;

import javax.websocket.EncodeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.kaleido.cordaconnector.model.common.FireflyReceiptNotification;
import io.kaleido.cordaconnector.model.common.TransactionInfo;
import io.kaleido.cordaconnector.ws.WebSocketConnection;

@Component
public class ReceiptService {
  private Logger logger = LoggerFactory.getLogger("ReceiptService");
  private BlockingQueue<WebSocketConnection> receiptListeners;

  public ReceiptService() {
    this.receiptListeners = new LinkedBlockingDeque<WebSocketConnection>();
  }

  public void addReceiptListener(WebSocketConnection wsConn) {
    logger.info("Adding receipt listener client with connection id {}", wsConn.getId());
    this.receiptListeners.add(wsConn);
  }

  public void broadcastReceipt(FireflyReceiptNotification receipt) {
    logger.info("Broadcasting receipt {}", receipt);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(this.receiptListeners.size());

    for (WebSocketConnection wsConn : this.receiptListeners) {
      scheduler.submit(new Runnable() {
        @Override
        public void run() {
          try {
            wsConn.sendData(receipt);
          } catch (IOException | EncodeException e) {
            e.printStackTrace();
          }
        }
      });
    }
  }
}
