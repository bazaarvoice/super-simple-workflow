package com.bazaarvoice.sswf;

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.bazaarvoice.sswf.service.StepActionWorker;
import com.bazaarvoice.sswf.service.StepDecisionWorker;
import com.bazaarvoice.sswf.service.WorkflowManagement;
import scala.reflect.ClassTag$;

public class Builders {
    public static class StepActionWorkerBuilder<SSWFInput, StepEnum extends Enum<StepEnum> & WorkflowStep> {
        private final Class<StepEnum> stepEnumClass;
        private String domain;
        private String taskList;
        private AmazonSimpleWorkflow swf;
        private InputParser<SSWFInput> inputParser;
        private WorkflowDefinition<SSWFInput, StepEnum> workflowDefinition;
        private Logger logger;

        /**
         * Convenience chainable builder for {@link StepActionWorker}. You can also just call the constructor if that suits you better. The constructor
         * may be preferable from scala code, since the ClassTag will be generated automatically.
         * <p>
         * We require you to provide references to your generic parameters' classes so that we can use reflection on them later (e.g., to generate Enum members from their names)
         *
         * @param inputClass    The class of your workflow input
         * @param stepEnumClass The class of your step enum
         */
        public StepActionWorkerBuilder(@SuppressWarnings("UnusedParameters") Class<SSWFInput> inputClass, Class<StepEnum> stepEnumClass) {
            // inputClass is an argument only to set the generic type (and to future-proof in case we ever need a classTag for it).
            this.stepEnumClass = stepEnumClass;
        }

        /**
         * @return a new {@link StepActionWorker}
         */
        public StepActionWorker<SSWFInput, StepEnum> build() {
            if (stepEnumClass == null) throw new IllegalArgumentException("stepEnumClass was null");
            if (domain == null) throw new IllegalArgumentException("domain was null");
            if (taskList == null) throw new IllegalArgumentException("taskList was null");
            if (swf == null) throw new IllegalArgumentException("swf was null");
            if (inputParser == null) throw new IllegalArgumentException("inputParser was null");
            if (workflowDefinition == null) throw new IllegalArgumentException("workflowDefinition was null");
            if (logger == null) throw new IllegalArgumentException("logger was null");
            return new StepActionWorker<>(domain, taskList, swf, inputParser, workflowDefinition, logger, ClassTag$.MODULE$.apply(stepEnumClass));
        }

        /**
         * @param domain The domain of the workflow: <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-domain.html">AWS docs</a>
         *               as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
         * @return The builder
         */
        public StepActionWorkerBuilder<SSWFInput, StepEnum> setDomain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * @param taskList If you execute the same workflow in different environments, use different task lists. Think of them as independent sets of actors working on the same logical workflow, but in
         *                 different contexts (like production/qa/development/your machine).
         *                 <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-task-lists.html">AWS docs</a>
         *                 as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
         * @return The builder.
         */
        public StepActionWorkerBuilder<SSWFInput, StepEnum> setTaskList(String taskList) {
            this.taskList = taskList;
            return this;
        }

        /**
         * @param swf The AWS SWF client
         * @return The builder
         */
        public StepActionWorkerBuilder<SSWFInput, StepEnum> setSwf(AmazonSimpleWorkflow swf) {
            this.swf = swf;
            return this;
        }

        /**
         * @param inputParser See {@link InputParser}
         * @return The builder
         */
        public StepActionWorkerBuilder<SSWFInput, StepEnum> setInputParser(InputParser<SSWFInput> inputParser) {
            this.inputParser = inputParser;
            return this;
        }

        /**
         * @param workflowDefinition See {@link WorkflowDefinition}
         * @return The builder
         */
        public StepActionWorkerBuilder<SSWFInput, StepEnum> setWorkflowDefinition(WorkflowDefinition<SSWFInput, StepEnum> workflowDefinition) {
            this.workflowDefinition = workflowDefinition;
            return this;
        }

        /**
         * @param logger
         * @return The builder
         */
        public StepActionWorkerBuilder<SSWFInput, StepEnum> setLogger(Logger logger) {
            this.logger = logger;
            return this;
        }
    }

    public static class StepDecisionWorkerBuilder<SSWFInput, StepEnum extends Enum<StepEnum> & WorkflowStep> {
        private final Class<StepEnum> stepEnumClass;
        private String domain;
        private String taskList;
        private AmazonSimpleWorkflow swf;
        private InputParser<SSWFInput> inputParser;
        private WorkflowDefinition<SSWFInput, StepEnum> workflowDefinition;
        private Logger logger;

        public StepDecisionWorkerBuilder(@SuppressWarnings("UnusedParameters") Class<SSWFInput> inputClass, Class<StepEnum> stepEnumClass) {
            // inputClass is an argument only to set the generic type (and to future-proof in case we ever need a classTag for it).
            this.stepEnumClass = stepEnumClass;
        }

        public StepDecisionWorker<SSWFInput, StepEnum> build() {
            if (stepEnumClass == null) throw new IllegalArgumentException("stepEnumClass was null");
            if (domain == null) throw new IllegalArgumentException("domain was null");
            if (taskList == null) throw new IllegalArgumentException("taskList was null");
            if (swf == null) throw new IllegalArgumentException("swf was null");
            if (inputParser == null) throw new IllegalArgumentException("inputParser was null");
            if (workflowDefinition == null) throw new IllegalArgumentException("workflowDefinition was null");
            if (logger == null) throw new IllegalArgumentException("logger was null");
            return new StepDecisionWorker<>(domain, taskList, swf, inputParser, workflowDefinition, logger, ClassTag$.MODULE$.apply(stepEnumClass));
        }


        /**
         * @param domain The domain of the workflow: <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-domain.html">AWS docs</a>
         *               as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
         * @return The builder
         */
        public StepDecisionWorkerBuilder<SSWFInput, StepEnum> setDomain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * @param taskList If you execute the same workflow in different environments, use different task lists. Think of them as independent sets of actors working on the same logical workflow, but in
         *                 different contexts (like production/qa/development/your machine).
         *                 <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-task-lists.html">AWS docs</a>
         *                 as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
         * @return The builder.
         */
        public StepDecisionWorkerBuilder<SSWFInput, StepEnum> setTaskList(String taskList) {
            this.taskList = taskList;
            return this;
        }

        /**
         * @param swf The AWS SWF client
         * @return The builder
         */
        public StepDecisionWorkerBuilder<SSWFInput, StepEnum> setSwf(AmazonSimpleWorkflow swf) {
            this.swf = swf;
            return this;
        }

        /**
         * @param inputParser See {@link InputParser}
         * @return The builder
         */
        public StepDecisionWorkerBuilder<SSWFInput, StepEnum> setInputParser(InputParser<SSWFInput> inputParser) {
            this.inputParser = inputParser;
            return this;
        }

        /**
         * @param workflowDefinition See {@link WorkflowDefinition}
         * @return The builder
         */
        public StepDecisionWorkerBuilder<SSWFInput, StepEnum> setWorkflowDefinition(WorkflowDefinition<SSWFInput, StepEnum> workflowDefinition) {
            this.workflowDefinition = workflowDefinition;
            return this;
        }

        /**
         * @param logger
         * @return The builder
         */
        public StepDecisionWorkerBuilder<SSWFInput, StepEnum> setLogger(Logger logger) {
            this.logger = logger;
            return this;
        }
    }

    public static class WorkflowManagementBuilder<SSWFInput, StepEnum extends Enum<StepEnum> & WorkflowStep> {
        private final Class<StepEnum> stepEnumClass;
        private String domain;
        private String taskList;
        private AmazonSimpleWorkflow swf;
        private InputParser<SSWFInput> inputParser;
        private String workflow;
        private String workflowVersion;
        private int workflowExecutionTimeoutSeconds = 60 * 60 * 24 * 30; // default: one month
        private int workflowExecutionRetentionPeriodDays = 30;
        private int stepScheduleToStartTimeoutSeconds = 60;
        private Logger logger;

        public WorkflowManagementBuilder(@SuppressWarnings("UnusedParameters") Class<SSWFInput> inputClass, Class<StepEnum> stepEnumClass) {
            // inputClass is an argument only to set the generic type (and to future-proof in case we ever need a classTag for it).
            this.stepEnumClass = stepEnumClass;
        }

        public WorkflowManagement<SSWFInput, StepEnum> build() {
            if (stepEnumClass == null) throw new IllegalArgumentException("stepEnumClass was null");
            if (domain == null) throw new IllegalArgumentException("domain was null");
            if (workflow == null) throw new IllegalArgumentException("workflow was null");
            if (workflowVersion == null) throw new IllegalArgumentException("workflowVersion was null");
            if (taskList == null) throw new IllegalArgumentException("taskList was null");
            if (swf == null) throw new IllegalArgumentException("swf was null");
            if (workflowExecutionTimeoutSeconds <= 0) throw new IllegalArgumentException("workflowExecutionTimeoutSeconds must be set > 0");
            if (workflowExecutionRetentionPeriodDays <= 0) throw new IllegalArgumentException("workflowExecutionRetentionPeriodDays must be set > 0");
            if (stepScheduleToStartTimeoutSeconds <= 0) throw new IllegalArgumentException("stepScheduleToStartTimeoutSeconds must be set > 0");
            if (inputParser == null) throw new IllegalArgumentException("inputParser was null");
            if (logger == null) throw new IllegalArgumentException("logger was null");
            return new WorkflowManagement<SSWFInput, StepEnum>(domain, workflow, workflowVersion, taskList, swf, workflowExecutionTimeoutSeconds, workflowExecutionRetentionPeriodDays, stepScheduleToStartTimeoutSeconds, inputParser, logger, ClassTag$.MODULE$.apply(stepEnumClass));
        }


        /**
         * @param domain The domain of the workflow: <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-domain.html">AWS docs</a>
         *               as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
         * @return The builder
         */
        public WorkflowManagementBuilder<SSWFInput, StepEnum> setDomain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * @param taskList If you execute the same workflow in different environments, use different task lists. Think of them as independent sets of actors working on the same logical workflow, but in
         *                 different contexts (like production/qa/development/your machine).
         *                 <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-task-lists.html">AWS docs</a>
         *                 as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things">the README</a>
         * @return The builder.
         */
        public WorkflowManagementBuilder<SSWFInput, StepEnum> setTaskList(String taskList) {
            this.taskList = taskList;
            return this;
        }

        /**
         * @param swf The AWS SWF client
         * @return The builder
         */
        public WorkflowManagementBuilder<SSWFInput, StepEnum> setSwf(AmazonSimpleWorkflow swf) {
            this.swf = swf;
            return this;
        }


        /**
         * @param inputParser See {@link InputParser}
         * @return The builder
         */
        public WorkflowManagementBuilder<SSWFInput, StepEnum> setInputParser(InputParser<SSWFInput> inputParser) {
            this.inputParser = inputParser;
            return this;
        }

        /**
         * @param workflow The id of your particular workflow. See "WorkflowType" in <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-obj-ident.html">the AWS docs</a>
         *                 as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things">the README</a>.
         * @return the builder
         */
        public WorkflowManagementBuilder<SSWFInput, StepEnum> setWorkflow(String workflow) {
            this.workflow = workflow;
            return this;
        }

        /**
         * @param workflowVersion You can version workflows, although it's not clear what purpose that serves. Advice: just think of this as an administrative notation. See
         *                        <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things">the README</a>.
         * @return The builder
         */
        public WorkflowManagementBuilder<SSWFInput, StepEnum> setWorkflowVersion(String workflowVersion) {
            this.workflowVersion = workflowVersion;
            return this;
        }

        /**
         * @param workflowExecutionTimeoutSeconds How long to let the entire workflow run. This only comes in to play if 1) The decision threads die or 2) A step gets into an "infinite loop" in which it
         *                                        always returns InProgress without making any actual progress. The default is set to one month on the assumption that you'll monitor the workflow and
         *                                        fix either of those problems if they occur, letting the workflow resume and complete. If you prefer to let the workflow fail, you'll want to set it lower.
         * @return The builder
         */
        public WorkflowManagementBuilder<SSWFInput, StepEnum> setWorkflowExecutionTimeoutSeconds(int workflowExecutionTimeoutSeconds) {
            this.workflowExecutionTimeoutSeconds = workflowExecutionTimeoutSeconds;
            return this;
        }

        /**
         * @param workflowExecutionRetentionPeriodDays How long to keep _completed_ workflow information. Default: one month.
         * @return The builder
         */
        public WorkflowManagementBuilder<SSWFInput, StepEnum> setWorkflowExecutionRetentionPeriodDays(int workflowExecutionRetentionPeriodDays) {
            this.workflowExecutionRetentionPeriodDays = workflowExecutionRetentionPeriodDays;
            return this;
        }

        /**
         * @param stepScheduleToStartTimeoutSeconds The duration you expect to pass _after_ a task is scheduled, and _before_ an actionWorker picks it up. If there is always a free actionWorker, this is
         *                                          just the polling interval for actions to execute. If all the actionWorkers are busy, though, the action may time out waiting to start. This isn't
         *                                          harmful, though, since the decisionWorker will simply re-schedule it. Advice: make your actionWorker pool large enough that all scheduled work can
         *                                          execute immediately, and set this timeout to the polling interval for action work. Default: 60s
         * @return The builder
         */
        public WorkflowManagementBuilder<SSWFInput, StepEnum> setStepScheduleToStartTimeoutSeconds(int stepScheduleToStartTimeoutSeconds) {
            this.stepScheduleToStartTimeoutSeconds = stepScheduleToStartTimeoutSeconds;
            return this;
        }

        /**
         * @param logger
         * @return The builder
         */
        public WorkflowManagementBuilder<SSWFInput, StepEnum> setLogger(Logger logger) {
            this.logger = logger;
            return this;
        }
    }
}
