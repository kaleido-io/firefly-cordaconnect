// Copyright © 2022 Kaleido, Inc.
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

package corda

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/hyperledger/firefly-common/pkg/config"
	"github.com/hyperledger/firefly-common/pkg/fftypes"
	"github.com/hyperledger/firefly-common/pkg/log"
	"github.com/hyperledger/firefly-transaction-manager/pkg/ffcapi"
)

// Corda does not have blocks or events. Just contract states.
// Each state is uniquely identified by a transaction hash and index of the UTXO output
// produced by the transaction
type stateUpdateConsumer struct {
	id      *fftypes.UUID // could be an event stream ID for example - must be unique
	ctx     context.Context
	updates chan<- *ffcapi.BlockHashEvent // channel to send state updates on
}

type CordaStateData struct {
	Data       map[string]interface{} `json:"data"`
	Contract   string                 `json:"contract"`
	Notary     string                 `json:"notary"`
	Constraint map[string]interface{} `json:"constraint"`
}

type CordaStateRef struct {
	TxHash string `json:"txhash"`
	Index  int    `json:"index"`
}

type CordaState struct {
	Data           CordaStateData `json:"data"`
	SubscriptionID string         `json:"subId"`
	TypeSignature  string         `json:"signature"`
	StateRef       CordaStateRef  `json:"stateRef"`
	RecordedTime   fftypes.FFTime `json:"recordedTime"`
}

// stateListener has two functions:
// 1) To establish and keep track of what the latest state of the Corda node is, so event streams know how far from the "head" they are
// 2) To feed new state ("event") information to any registered consumers
type stateListener struct {
	ctx                      context.Context
	c                        *cordaConnector
	listenLoopDone           chan struct{}
	initialTimestampObtained chan struct{}
	latestTimestamp          *fftypes.FFTime
	mux                      sync.Mutex
	consumers                map[fftypes.UUID]*stateUpdateConsumer
	statePollingInterval     time.Duration
}

func newStateListener(ctx context.Context, c *cordaConnector, conf config.Section) *stateListener {
	zeroTime := fftypes.ZeroTime()
	sl := &stateListener{
		ctx:                      log.WithLogField(ctx, "role", "statelistener"),
		c:                        c,
		initialTimestampObtained: make(chan struct{}),
		latestTimestamp:          &zeroTime,
		consumers:                make(map[fftypes.UUID]*stateUpdateConsumer),
		statePollingInterval:     conf.GetDuration(StatePollingInterval),
	}
	return sl
}

// establishLatestTimestampWithRetry keeps retrying attempting to get the initial time stamp until successful
func (bl *stateListener) establishLatestTimestampWithRetry() error {
	return bl.c.retry.Do(bl.ctx, "get initial time stamp", func(attempt int) (retry bool, err error) {
		timestamp, err := bl.c.backend.GetLatestStateTimestamp(bl.ctx)
		if err != nil {
			log.L(bl.ctx).Warnf("Failed to get initial timestamp: %s", err)
			return true, nil
		}
		bl.mux.Lock()
		bl.latestTimestamp = timestamp
		bl.mux.Unlock()
		return false, nil
	})
}

func (bl *stateListener) listenLoop() {
	defer close(bl.listenLoopDone)

	err := bl.establishLatestTimestampWithRetry()
	close(bl.initialTimestampObtained)
	if err != nil {
		log.L(bl.ctx).Warnf("state listener exiting before establishing initial time stamp: %s", err)
	}

	c, _, err := websocket.DefaultDialer.Dial("ws://localhost:8000/ws", nil)
	if err != nil {
		// TODO, handle this error and retry
		log.L(bl.ctx).Errorf("dial error: %s", err)
		return
	}

	relay := make(chan []byte)
	go func(relay chan []byte) {
		_, message, err := c.ReadMessage()
		if err != nil {
			log.L(bl.ctx).Errorf("websocket read error: %s", err)
		} else {
			relay <- message
		}
	}(relay)

	for {
		// Sleep for the update interval, and see if more state updates have come in
		select {
		case message := <-relay:
			states := []CordaState{}
			err := json.Unmarshal(message, &states)
			if err != nil {
				log.L(bl.ctx).Errorf("Failed to unmarshal state update: %s", err)
			} else {
				update := ffcapi.BlockHashEvent{GapPotential: false}
				bl.mux.Lock()
				for _, state := range states {
					if state.RecordedTime.Time().After(*bl.latestTimestamp.Time()) {
						bl.latestTimestamp = &state.RecordedTime
					}
					hashAndIndex := fmt.Sprintf("%s:%d", state.StateRef.TxHash, state.StateRef.Index)
					update.BlockHashes = append(update.BlockHashes, hashAndIndex)
				}

				// Take a copy of the consumers in the lock
				consumers := make([]*stateUpdateConsumer, 0, len(bl.consumers))
				for _, c := range bl.consumers {
					consumers = append(consumers, c)
				}
				bl.mux.Unlock()
				bl.dispatchToConsumers(consumers, &update)
			}
		case <-bl.ctx.Done():
			log.L(bl.ctx).Debugf("State listener loop stopping")
			return
		}
	}
}

func (bl *stateListener) dispatchToConsumers(consumers []*stateUpdateConsumer, update *ffcapi.BlockHashEvent) {
	for _, c := range consumers {
		log.L(bl.ctx).Tracef("Notifying consumer %s of blocks %v (gap=%t)", c.id, update.BlockHashes, update.GapPotential)
		select {
		case c.updates <- update:
		case <-bl.ctx.Done(): // loop, we're stopping and will exit on next loop
		case <-c.ctx.Done():
			log.L(bl.ctx).Debugf("Block update consumer %s closed", c.id)
			bl.mux.Lock()
			delete(bl.consumers, *c.id)
			bl.mux.Unlock()
		}
	}
}

func (bl *stateListener) checkStartedLocked() {
	if bl.listenLoopDone == nil {
		bl.listenLoopDone = make(chan struct{})
		go bl.listenLoop()
	}
}

func (bl *stateListener) addConsumer(c *stateUpdateConsumer) {
	bl.mux.Lock()
	defer bl.mux.Unlock()
	bl.checkStartedLocked()
	bl.consumers[*c.id] = c
}

func (bl *stateListener) getLatestState(ctx context.Context) fftypes.FFTime {
	bl.mux.Lock()
	bl.checkStartedLocked()
	latestStateTimestamp := bl.latestTimestamp
	bl.mux.Unlock()
	// if not yet initialized, wait to be initialized
	zeroTime := fftypes.ZeroTime()
	if latestStateTimestamp.Equal(&zeroTime) {
		select {
		case <-bl.initialTimestampObtained:
		case <-ctx.Done():
		}
	}
	bl.mux.Lock()
	latestStateTimestamp = bl.latestTimestamp
	bl.mux.Unlock()
	log.L(ctx).Debugf("Latest State Timestamp=%d", latestStateTimestamp)
	return *latestStateTimestamp
}

func (bl *stateListener) waitClosed() {
	bl.mux.Lock()
	listenLoopDone := bl.listenLoopDone
	bl.mux.Unlock()
	if listenLoopDone != nil {
		<-listenLoopDone
	}
}
