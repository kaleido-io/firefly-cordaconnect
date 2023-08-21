// Copyright © 2023 Kaleido, Inc.
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
	"sync"
	"time"

	lru "github.com/hashicorp/golang-lru"
	"github.com/hyperledger/firefly-common/pkg/config"
	"github.com/hyperledger/firefly-common/pkg/ffresty"
	"github.com/hyperledger/firefly-common/pkg/fftypes"
	"github.com/hyperledger/firefly-common/pkg/i18n"
	"github.com/hyperledger/firefly-common/pkg/log"
	"github.com/hyperledger/firefly-common/pkg/retry"
	"github.com/hyperledger/firefly-cordaconnect/internal/backend"
	"github.com/hyperledger/firefly-cordaconnect/internal/msgs"
	"github.com/hyperledger/firefly-transaction-manager/pkg/ffcapi"
)

type cordaConnector struct {
	backend       backend.Backend
	retry         *retry.Retry
	mux           sync.Mutex
	stateListener *stateListener
	eventStreams  map[fftypes.UUID]*eventStream
	blockCache    *lru.Cache
	txCache       *lru.Cache
}

func NewCordaConnector(ctx context.Context, conf config.Section) (cc ffcapi.API, err error) {
	c := &cordaConnector{
		eventStreams: make(map[fftypes.UUID]*eventStream),
		retry: &retry.Retry{
			InitialDelay: conf.GetDuration(RetryInitDelay),
			MaximumDelay: conf.GetDuration(RetryMaxDelay),
			Factor:       conf.GetFloat64(RetryFactor),
		},
	}
	c.blockCache, err = lru.New(conf.GetInt(StateCacheSize))
	if err != nil {
		return nil, i18n.WrapError(ctx, err, msgs.MsgCacheInitFail, "state")
	}

	c.txCache, err = lru.New(conf.GetInt(TxCacheSize))
	if err != nil {
		return nil, i18n.WrapError(ctx, err, msgs.MsgCacheInitFail, "transaction")
	}

	if conf.GetString(ffresty.HTTPConfigURL) == "" {
		return nil, i18n.NewError(ctx, msgs.MsgMissingBackendURL)
	}

	httpClient, err := ffresty.New(ctx, conf)
	if err != nil {
		return nil, err
	}
	c.backend = backend.NewRPCClientWithOption(httpClient, backend.RPCClientOptions{
		MaxConcurrentRequest: conf.GetInt64(MaxConcurrentRequests),
	})

	c.stateListener = newStateListener(ctx, c, conf)

	return c, nil
}

// WaitClosed can be called after cancelling all the contexts, to wait for everything to close down
func (c *cordaConnector) WaitClosed() {
	if c.stateListener != nil {
		c.stateListener.waitClosed()
	}
	for _, s := range c.eventStreams {
		<-s.streamLoopDone
	}
}

func (c *cordaConnector) doFailureDelay(ctx context.Context, failureCount int) bool {
	if failureCount <= 0 {
		return false
	}

	retryDelay := c.retry.InitialDelay
	for i := 0; i < (failureCount - 1); i++ {
		retryDelay = time.Duration(float64(retryDelay) * c.retry.Factor)
		if retryDelay > c.retry.MaximumDelay {
			retryDelay = c.retry.MaximumDelay
			break
		}
	}
	log.L(ctx).Debugf("Retrying after %.2f (failures=%d)", retryDelay.Seconds(), failureCount)
	select {
	case <-time.After(retryDelay):
		return false
	case <-ctx.Done():
		return true
	}
}
