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
	"github.com/hyperledger/firefly-common/pkg/config"
	"github.com/hyperledger/firefly-common/pkg/ffresty"
)

const (
	StatePollingInterval  = "statePollingInterval"
	StateCacheSize        = "stateCacheSize"
	RetryInitDelay        = "retry.initialDelay"
	RetryMaxDelay         = "retry.maxDelay"
	RetryFactor           = "retry.factor"
	MaxConcurrentRequests = "maxConcurrentRequests"
	TxCacheSize           = "txCacheSize"
)

const (
	DefaultListenerPort     = 5102
	DefaultRetryInitDelay   = "100ms"
	DefaultRetryMaxDelay    = "30s"
	DefaultRetryDelayFactor = 2.0
)

func InitConfig(conf config.Section) {
	ffresty.InitConfig(conf)
	conf.AddKnownKey(StateCacheSize, 250)
	conf.AddKnownKey(StatePollingInterval, "1s")
	conf.AddKnownKey(RetryFactor, DefaultRetryDelayFactor)
	conf.AddKnownKey(RetryInitDelay, DefaultRetryInitDelay)
	conf.AddKnownKey(RetryMaxDelay, DefaultRetryMaxDelay)
	conf.AddKnownKey(MaxConcurrentRequests, 50)
	conf.AddKnownKey(TxCacheSize, 250)
}
