// Copyright © 2021 Kaleido, Inc.
//
// SPDX-License-Identifier: Apache-2.0
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.kaleido.cordaconnector.rpc;

import io.kaleido.cordaconnector.config.CordaRPCConfig;
import io.kaleido.cordaconnector.exception.CordaConnectionException;
import net.corda.client.rpc.CordaRPCClient;
import net.corda.client.rpc.CordaRPCConnection;
import net.corda.client.rpc.GracefulReconnect;
import net.corda.client.rpc.RPCException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.NetworkHostAndPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.subjects.PublishSubject;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class NodeRPCClient {
  private static final Logger logger = LoggerFactory.getLogger(NodeRPCClient.class);
  private CordaRPCOps rpcProxy;
  private CordaRPCConnection connection;
  private final CordaRPCConfig rpcConfig;
  private PublishSubject<Boolean> connectionRx;

  public NodeRPCClient(CordaRPCConfig config) {
    logger.info(config.toString());
    rpcConfig = config;
    connectionRx = PublishSubject.create();
  }

  @PostConstruct
  public void connectToCorda() {
    boolean connectionEstablished = false;
    long waitBeforeRetry = 1000;
    long delayMultiplier = 2;
    long retries = 0;
    long maxRetries = 10;
    while (!connectionEstablished) {
      try {
        logger.info("Attempting to connect to corda.");
        this.getRpcProxy();
        connectionEstablished = true;
        logger.info("Successfully connected to corda.");
      } catch (CordaConnectionException e) {
        logger.info("Failed to connect to corda on startup. retrying attempt {}",
            retries, e);
      }
      if (retries == maxRetries) {
        logger.error("Failed to connect to corda on startup after {} attempts, exiting.",
            maxRetries);
        System.exit(1);
      }
      waitBeforeRetry *= delayMultiplier;
      retries++;
      if (!connectionEstablished) {
        try {
          Thread.sleep(waitBeforeRetry);
        } catch (InterruptedException e) {
          logger.error("Thread interrupted while waiting before retrying.", e);
        }
      }
    }
  }

  public Observable<Boolean> getRpcConnectionObservable() {
    return connectionRx.asObservable();
  }

  public CordaRPCOps getRpcProxy() throws CordaConnectionException {
    // Only attempt connection when object doesn't exist.
    if (rpcProxy == null) {
      NetworkHostAndPort rpcServer = NetworkHostAndPort.parse(rpcConfig.getHost());
      logger.info("Initializing rpc connection. {} {}", rpcServer.getHost(), rpcServer.getPort());
      // Allows us to do take actions in case of connect and disconnect event.
      GracefulReconnect gracefulReconnect = new GracefulReconnect(
          () -> {
            logger.info("client disconnected.");
            connectionRx.onNext(false);
          },
          () -> {
            logger.info("client connected.");
            rpcProxy = connection.getProxy();
            connectionRx.onNext(true);
          });
      try {
        connection = new CordaRPCClient(rpcServer).start(rpcConfig.getUsername(), rpcConfig.getPassword(),
            gracefulReconnect);
        rpcProxy = connection.getProxy();
      } catch (RPCException re) {
        logger.debug("failed to establish connection");
        connectionRx.onNext(false);
        throw new CordaConnectionException(re.getMessage(), re);
      }
    } else {
      logger.debug("rpc connection already established. {}", rpcConfig.getHost());
    }
    return rpcProxy;
  }

  public void sendTransaction(Class<? extends FlowLogic<? extends SignedTransaction>> clazz, Object... args) {
    try {
      getRpcProxy().startFlowDynamic(clazz, args).getReturnValue().get();
    } catch (Exception e) {
      logger.error("failed to send transaction", e);
    }
  }

  @PreDestroy
  public void close() {
    if (connection != null)
      connection.notifyServerAndClose();
  }
}
