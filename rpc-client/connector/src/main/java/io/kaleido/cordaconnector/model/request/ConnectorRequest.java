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

package io.kaleido.cordaconnector.model.request;

public class ConnectorRequest<T> {
    private T data;
    private ConnectorRequestHeaders headers;

    public ConnectorRequest() {
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public ConnectorRequestHeaders getHeaders() {
        if (headers == null) {
            headers = new ConnectorRequestHeaders();
        }
        return headers;
    }

    public void setHeaders(ConnectorRequestHeaders headers) {
        this.headers = headers;
    }
}
