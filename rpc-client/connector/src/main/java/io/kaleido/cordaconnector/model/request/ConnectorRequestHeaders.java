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

import java.util.UUID;

public class ConnectorRequestHeaders {
  private String type;
  private String id;

  public ConnectorRequestHeaders() {
  }

  public String getType() {
    return type;
  }

  public String getId() {
    if (id == null) {
      id = UUID.randomUUID().toString();
    }

    return id;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setId(String requestId) {
    this.id = requestId;
  }
}
