/*
 * Copyright 2018 BSC Msc, LLC
 *
 * This file is part of the ATF project
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

package ru.bsc.test.at.executor.step.executor.requester;

import ru.bsc.test.at.executor.helper.client.api.ClientResponse;
import ru.bsc.test.at.executor.helper.client.impl.mq.MqClient;
import ru.bsc.test.at.executor.model.Project;
import ru.bsc.test.at.executor.model.Step;
import ru.bsc.test.at.executor.model.StepResult;

import java.util.Map;

/**
 * @author Pavel Golovkin
 */
public class MqSimpleStepRequester extends MqAbstractStepRequester {

    public MqSimpleStepRequester(StepResult stepResult, Step step, String requestBody, String testId, Project project, MqClient mqClient, Map<String, Object> scenarioVariables, String projectPath) {
        super(stepResult, step, requestBody, testId, project, mqClient, scenarioVariables, projectPath);
    }

    @Override
    protected ClientResponse call() throws Exception {

        ClientResponse response = getClientResponse();
        stepResult.setPollingRetryCount(1);

        return response;
    }
}