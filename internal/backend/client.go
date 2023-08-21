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

package backend

import (
	"context"
	"encoding/json"
	"fmt"
	"sync/atomic"
	"time"

	"github.com/go-resty/resty/v2"
	"github.com/hyperledger/firefly-common/pkg/fftypes"
	"github.com/hyperledger/firefly-common/pkg/log"
	"github.com/pkg/errors"
	"github.com/sirupsen/logrus"
)

// Backend performs communication with a backend
type Backend interface {
	SyncRequest(ctx context.Context, rpcReq *RPCRequest) (rpcRes *RPCResponse, err error)
	GetLatestStateTimestamp(ctx context.Context) (*fftypes.FFTime, error)
}

// NewClient creates a new Corda client
func NewRPCClientWithOption(client *resty.Client, options RPCClientOptions) Backend {
	rpcClient := &RPCClient{
		client: client,
	}

	if options.MaxConcurrentRequest > 0 {
		rpcClient.concurrencySlots = make(chan bool, options.MaxConcurrentRequest)
	}

	return rpcClient
}

type RPCClient struct {
	client           *resty.Client
	concurrencySlots chan bool
	requestCounter   int64
}

type RPCClientOptions struct {
	MaxConcurrentRequest int64
}

type RPCRequest struct {
	ID     *fftypes.JSONAny   `json:"id"`
	Params []*fftypes.JSONAny `json:"data,omitempty"`
}

type RPCError struct {
	Message string          `json:"message"`
	Data    fftypes.JSONAny `json:"data,omitempty"`
}

func (e *RPCError) Error() error {
	return fmt.Errorf(e.Message)
}

type RPCResponse struct {
	ID     *fftypes.JSONAny `json:"id"`
	Result *fftypes.JSONAny `json:"result,omitempty"`
	Error  *RPCError        `json:"error,omitempty"`
}

func (r *RPCResponse) Message() string {
	if r.Error != nil {
		return r.Error.Message
	}
	return ""
}

func (rc *RPCClient) allocateRequestID(req *RPCRequest) string {
	reqID := fmt.Sprintf(`%.9d`, atomic.AddInt64(&rc.requestCounter, 1))
	req.ID = fftypes.JSONAnyPtr(`"` + reqID + `"`)
	return reqID
}

// InitClient initializes the Corda client
func (c *RPCClient) GetLatestStateTimestamp(ctx context.Context) (*fftypes.FFTime, error) {
	result := make(map[string]interface{})
	res, err := c.client.R().
		SetContext(ctx).
		SetResult(&result).
		SetError(result).
		Get("http://localhost:8000/states?sort=desc&pageSize=1")
	if err != nil {
		t := fftypes.ZeroTime()
		return &t, err
	}
	// JSON/RPC allows errors to be returned with a 200 status code, as well as other status codes
	if res.IsError() {
		log.L(ctx).Errorf("RPC[] <-- [%d]: %s", res.StatusCode(), res.Error())
		t := fftypes.ZeroTime()
		return &t, err
	}

	metadata := result["statesMetadata"]
	metadataArray := metadata.([]interface{})
	if len(metadataArray) > 0 {
		metadata := metadataArray[0].(map[string]interface{})
		timestamp := metadata["recordedTime"].(string)
		parsed, err := fftypes.ParseTimeString(timestamp)
		if err != nil {
			t := fftypes.ZeroTime()
			return &t, err
		}
		return parsed, nil
	}
	t := fftypes.ZeroTime()
	return &t, nil
}

// SyncRequest sends an individual RPC request to the backend (always over HTTP currently),
// and waits synchronously for the response, or an error.
//
// In all return paths *including error paths* the RPCResponse is populated
// so the caller has an RPC structure to send back to the front-end caller.
func (rc *RPCClient) SyncRequest(ctx context.Context, rpcReq *RPCRequest) (rpcRes *RPCResponse, err error) {
	if rc.concurrencySlots != nil {
		select {
		case rc.concurrencySlots <- true:
			// wait for the concurrency slot and continue
		case <-ctx.Done():
			return nil, errors.Errorf("request %s has been canceled", rpcReq.ID)
		}
		defer func() {
			<-rc.concurrencySlots
		}()
	}

	// We always set the back-end request ID - as we need to support requests coming in from
	// multiple concurrent clients on our front-end that might use clashing IDs.
	var beReq = *rpcReq
	rpcTraceID := rc.allocateRequestID(&beReq)
	if rpcReq.ID != nil {
		// We're proxying a request with front-end RPC ID - log that as well
		rpcTraceID = fmt.Sprintf("%s->%s", rpcReq.ID, rpcTraceID)
	}

	rpcRes = new(RPCResponse)

	log.L(ctx).Debugf("RPC[%s] -->", rpcTraceID)
	if logrus.IsLevelEnabled(logrus.TraceLevel) {
		jsonInput, _ := json.Marshal(rpcReq)
		log.L(ctx).Tracef("RPC[%s] INPUT: %s", rpcTraceID, jsonInput)
	}
	rpcStartTime := time.Now()
	res, err := rc.client.R().
		SetContext(ctx).
		SetBody(beReq).
		SetResult(&rpcRes).
		SetError(rpcRes).
		Post("")

	// Restore the original ID
	rpcRes.ID = rpcReq.ID
	if err != nil {
		err := errors.Errorf("RPC request failed: %s", err)
		log.L(ctx).Errorf("RPC[%s] <-- ERROR: %s", rpcTraceID, err)
		rpcRes = RPCErrorResponse(err, rpcReq.ID)
		return rpcRes, err
	}
	if logrus.IsLevelEnabled(logrus.TraceLevel) {
		jsonOutput, _ := json.Marshal(rpcRes)
		log.L(ctx).Tracef("RPC[%s] OUTPUT: %s", rpcTraceID, jsonOutput)
	}
	// JSON/RPC allows errors to be returned with a 200 status code, as well as other status codes
	if res.IsError() || rpcRes.Error != nil {
		log.L(ctx).Errorf("RPC[%s] <-- [%d]: %s", rpcTraceID, res.StatusCode(), rpcRes.Message())
		err := fmt.Errorf(rpcRes.Message())
		return rpcRes, err
	}
	log.L(ctx).Infof("RPC[%s] <-- [%d] OK (%.2fms)", rpcTraceID, res.StatusCode(), float64(time.Since(rpcStartTime))/float64(time.Millisecond))
	if rpcRes.Result == nil {
		// We don't want a result for errors, but a null success response needs to go in there
		rpcRes.Result = fftypes.JSONAnyPtr(fftypes.NullString)
	}
	return rpcRes, nil
}

func RPCErrorResponse(err error, id *fftypes.JSONAny) *RPCResponse {
	return &RPCResponse{
		ID: id,
		Error: &RPCError{
			Message: err.Error(),
		},
	}
}
