/*
 * Copyright 2018 BSC Msc, LLC
 *
 * This file is part of the AuTe Framework project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.bsc.test.at.executor.helper.client.impl.http;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import ru.bsc.test.at.executor.helper.client.api.ClientRequest;

import java.util.Map;

/**
 * @author Pavel Golovkin
 */
@Slf4j
public class ClientHttpRequest implements ClientRequest {
  protected final String url;
  protected final Object body;
  protected final HTTPMethod method;
  protected final Map headers;
  protected final String testId;
  protected final String testIdHeaderName;
  protected final boolean useResponseAsBase64;

  @Builder(builderMethodName = "defaultBuilder")
  public ClientHttpRequest(String url, Object body, HTTPMethod method, Map headers, String testId, String testIdHeaderName, boolean useResponseAsBase64) {
    this.url = url;
    this.body = body;
    this.method = method;
    this.headers = headers;
    this.testId = testId;
    this.testIdHeaderName = testIdHeaderName;
    this.useResponseAsBase64 = useResponseAsBase64;
  }

  @Override
  public String getResource() {
    return url;
  }

  @Override
  public Object getBody() {
    return body;
  }

  @Override
  public Map getHeaders() {
    return headers;
  }

  @Override
  public String getTestId() {
    return testId;
  }

  @Override
  public String getTestIdHeaderName() {
    return testIdHeaderName;
  }

  public boolean getUseResponseAsBase64() {
    return useResponseAsBase64;
  }

  public HTTPMethod getMethod() {
    return method;
  }


}
