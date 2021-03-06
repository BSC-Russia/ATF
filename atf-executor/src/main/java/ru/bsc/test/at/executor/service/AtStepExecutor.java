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

package ru.bsc.test.at.executor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import ru.bsc.test.at.executor.ei.wiremock.WireMockAdmin;
import ru.bsc.test.at.executor.exception.ScenarioStopException;
import ru.bsc.test.at.executor.helper.MqMockHelper;
import ru.bsc.test.at.executor.helper.ServiceRequestsComparatorHelper;
import ru.bsc.test.at.executor.model.*;
import ru.bsc.test.at.executor.service.api.Executor;
import ru.bsc.test.at.executor.service.api.StepExecutorRequest;
import ru.bsc.test.at.executor.step.executor.IStepExecutor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.springframework.util.CollectionUtils.isEmpty;
import static ru.bsc.test.at.executor.service.AtProjectExecutor.parseLongOrVariable;

/**
 * @author Pavel Golovkin
 */
@Slf4j
public class AtStepExecutor implements Executor<StepExecutorRequest> {

    private final ServiceRequestsComparatorHelper serviceRequestsComparatorHelper = new ServiceRequestsComparatorHelper();
    private final MqMockHelper mqMockHelper = new MqMockHelper();
    private final DelayUtilities delayUtilities = new DelayUtilities();

    @Override
    public void execute(StepExecutorRequest stepExecutorRequest) {
        Assert.notNull(stepExecutorRequest, "stepExecutorRequest must not be null");
        log.debug("executeSteps {}, {}, {}, {}, {}, {}, {}, {}",
                stepExecutorRequest.getStand(), stepExecutorRequest.getStepList(),
                stepExecutorRequest.getProject(), stepExecutorRequest.getStepResultList(), stepExecutorRequest.getHttpClient(),
                stepExecutorRequest.getScenarioVariables(), stepExecutorRequest.isStepEditable(), stepExecutorRequest.getProjectPath());
        if (stepExecutorRequest.getStepList() == null) {
            log.warn("executeSteps got empty stepList");
            return;
        }
        for (Step step : stepExecutorRequest.getStepList()) {
            applyGlobalRequestHeaders(stepExecutorRequest.getProject(), step);
            if (!step.getDisabled()) {
                for (StepParameterSet stepParameterSet : getParametersEnvironment(step.getStepParameterSetList())) {
                    StepResult stepResult = new StepResult(stepExecutorRequest.getProject().getCode(), step);
                    stepResult.setStart(new Date().getTime());
                    stepResult.setEditable(stepExecutorRequest.isStepEditable());
                    stepExecutorRequest.getStepResultList().add(stepResult);

                    // COM-123 Timeout
                    if (isNotEmpty(step.getTimeoutMs()) && !step.isTimeoutEachRepetition()) {
                        delayUtilities.delay(parseLongOrVariable(stepExecutorRequest.getScenarioVariables(), step.getTimeoutMs(), 0));
                    }

                    if (stepParameterSet.getStepParameterList() != null) {
                        stepParameterSet.getStepParameterList()
                                .forEach(stepParameter -> stepExecutorRequest.getScenarioVariables().put(stepParameter.getName().trim(), stepParameter.getValue()));
                        stepResult.setDescription(stepParameterSet.getDescription());
                    }
                    try (WireMockAdmin wireMockAdmin = getWiremockAdmin(stepExecutorRequest)) {
                        if (stepExecutorRequest.getStand() == null) {
                            log.error("Stand is not configured");
                            throw new Exception("Stand is not configured.");
                        }
                        String testId = stepExecutorRequest.getProject().getUseRandomTestId() ? UUID.randomUUID().toString() : "-";
                        stepResult.setTestId(testId);

                        IStepExecutor stepExecutor = findStepExecutor(step);
                        stepResult.setSavedParameters(stepExecutorRequest.getScenarioVariables().toString());
                        try {
                            stepExecutor.execute(wireMockAdmin, stepExecutorRequest.getConnection(), stepExecutorRequest.getStand(), stepExecutorRequest.getHttpClient(), stepExecutorRequest.getMqClient(), stepExecutorRequest.getScenarioVariables(), testId, stepExecutorRequest.getProject(), stepExecutorRequest.getScenario(), step, stepResult, stepExecutorRequest.getProjectPath());
                        } catch (Exception ex) {
                            getExpectedRequestIfAssertException(ex, stepExecutorRequest, wireMockAdmin, step, testId, stepResult);
                        }

                        delayUtilities.waitWiremockDelayFromGroovyScripts(step);
                        serviceRequestsComparatorHelper.assertTestCaseWSRequests(stepExecutorRequest.getProject(), stepExecutorRequest.getScenarioVariables(), wireMockAdmin, testId, step, stepResult);
                        mqMockHelper.assertMqRequests(wireMockAdmin, testId, step, stepExecutorRequest.getScenarioVariables(), stepExecutorRequest.getProject().getMqCheckCount(), stepExecutorRequest.getProject().getMqCheckInterval(), stepResult);

                        stepResult.setSavedParameters(stepExecutorRequest.getScenarioVariables().toString());
                        stepResult.setResult(StepResult.StepResultType.OK);
                    } catch (Exception e) {
                        log.error("Step {} finished with error:", step.getCode(), e);
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));

                        stepResult.setResult(StepResult.StepResultType.FAIL);
                        stepResult.setDetails(sw.toString().substring(0, Math.min(sw.toString().length(), 10000)));
                    } finally {
                        stepResult.setStop(new Date().getTime());
                    }

                    stepResult.setScenarioVariables(new HashMap<>(stepExecutorRequest.getScenarioVariables()));

                    if (stepExecutorRequest.getStopObserver() != null && stepExecutorRequest.getStopObserver().stop()) {
                        throw new ScenarioStopException("stop observer is null");
                    }
                }
            }
        }
    }

    private void getExpectedRequestIfAssertException(Exception ex, StepExecutorRequest stepExecutorRequest, WireMockAdmin wireMockAdmin, Step step, String testId, StepResult stepResult) throws Exception {
        try {
            boolean diffStatus = ex.getMessage() != null && ex.getMessage().startsWith("Expected status code");
            if (ex.getCause() instanceof AssertionError || diffStatus) {
                serviceRequestsComparatorHelper.assertTestCaseWSRequests(stepExecutorRequest.getProject(), stepExecutorRequest.getScenarioVariables(), wireMockAdmin, testId, step, stepResult);
                mqMockHelper.assertMqRequests(wireMockAdmin, testId, step, stepExecutorRequest.getScenarioVariables(), stepExecutorRequest.getProject().getMqCheckCount(), stepExecutorRequest.getProject().getMqCheckInterval(), stepResult);
            }
        } catch (Exception e) {
            log.error("[{}] Error during expectedRequest", step.getCode(), ex);
        }
        throw ex;
    }

    private List<StepParameterSet> getParametersEnvironment(List<StepParameterSet> parametersFromStep) {
        return isEmpty(parametersFromStep) ? singletonList(new StepParameterSet()) : parametersFromStep;
    }

    private IStepExecutor findStepExecutor(Step step) {
        return IStepExecutor.STEP_EXECUTORS.stream()
            .filter(executor -> executor.support(step))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Not found executor for step " + step.getCode()));
    }

    private WireMockAdmin getWiremockAdmin(StepExecutorRequest request) {
        final Stand stand = request.getStand();
        final String wireMockUrl = stand.getWireMockUrl();
        return isNotEmpty(wireMockUrl) ? new WireMockAdmin(wireMockUrl) : null;
    }

    /**
     * Unites global and local headers.
     * If local and global headers contains property with the same name than will be returned local property
     */
    private void applyGlobalRequestHeaders(Project project, Step step) {
        String requestHeaders = step.getRequestHeaders();
        String globalRequestHeaders = project.getGlobalRequestHeaders();

        if (StringUtils.isEmpty(globalRequestHeaders)) {
            return;
        }

        if (StringUtils.isEmpty(requestHeaders)) {
            step.setRequestHeaders(globalRequestHeaders);
        } else {
            Set<String> headerNamesSet = Arrays.stream(requestHeaders.split("\n"))
                                               .map(header -> header.split(":")[0])
                                               .map(String::trim)
                                               .collect(Collectors.toSet());

            StringBuilder resultRequestHeadersBuilder = new StringBuilder(requestHeaders);

            Arrays.stream(globalRequestHeaders.split("\n")).forEach(globalHeader -> {
                String globalHeaderName = globalHeader.split(":")[0].trim();
                if (!headerNamesSet.contains(globalHeaderName)) {
                    resultRequestHeadersBuilder.append("\n").append(globalHeader);
                }
            });

            step.setRequestHeaders(resultRequestHeadersBuilder.toString());
        }
    }

}
