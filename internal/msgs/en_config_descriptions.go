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

package msgs

import (
	"github.com/hyperledger/firefly-common/pkg/i18n"
	"golang.org/x/text/language"
)

var ffc = func(key, translation string, fieldType string) i18n.ConfigMessageKey {
	return i18n.FFC(language.AmericanEnglish, key, translation, fieldType)
}

//revive:disable
var (
	ConfigCordaURL              = ffc("config.connector.url", "URL of JSON/RPC endpoint for the Corda node/gateway", "string")
	ConfigCordaDataFormat       = ffc("config.connector.dataFormat", "Configure the JSON data format for query output and events", "map,flat_array,self_describing")
	ConfigTxCacheSize           = ffc("config.connector.txCacheSize", "Maximum of transactions to hold in the transaction info cache", i18n.IntType)
	ConfigMaxConcurrentRequests = ffc("config.connector.maxConcurrentRequests", "Maximum of concurrent requests to be submitted to the blockchain", i18n.IntType)
)
