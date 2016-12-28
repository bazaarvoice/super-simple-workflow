package com.bazaarvoice.sswf.service;

import com.bazaarvoice.sswf.WorkflowStep;
import scala.reflect.ClassTag$;

public class WorkerServiceFactories {
    private WorkerServiceFactories() {}

    public static <SSWFInput, StepEnum extends Enum<StepEnum> & WorkflowStep> DecisionService<SSWFInput, StepEnum> decisionService(StepDecisionWorker<SSWFInput, StepEnum> stepDecisionWorker,
                                                                                                                                   Class<StepEnum> enumClass) {
        return new DecisionService<SSWFInput, StepEnum>(stepDecisionWorker, ClassTag$.MODULE$.apply(enumClass));
    }

    public static <SSWFInput, StepEnum extends Enum<StepEnum> & WorkflowStep> ActionService<SSWFInput, StepEnum> actionService(StepActionWorker<SSWFInput, StepEnum> stepActionWorker,
                                                                                                                               int workPoolMaxSize,
                                                                                                                               Class<StepEnum> enumClass) {
        return new ActionService<SSWFInput, StepEnum>(stepActionWorker, workPoolMaxSize, ClassTag$.MODULE$.apply(enumClass));
    }
}
