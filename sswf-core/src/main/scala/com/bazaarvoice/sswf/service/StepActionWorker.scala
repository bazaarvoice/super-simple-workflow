package com.bazaarvoice.sswf.service

import java.lang.management.ManagementFactory

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf._
import com.bazaarvoice.sswf.model.result.StepResult
import com.bazaarvoice.sswf.util.unpackInput
import com.sun.istack.internal.{NotNull, Nullable}

import scala.reflect._

/**
  * The worker class responsible for executing workflow steps. The class is built so that you can plug it in to a scheduled service of your choice.
  * First, call <code>pollForWork()</code> to determine if a step needs to be executed, and then call <code>doWork()</code> to execute the step.
  * The actual logic for the step is provided by your <code>WorkflowDefinition#act()</code>.
  * <br/>
  * You could obviously poll and work in the same thread, but remember that this worker can handle many concurrent workflows, so separating them lets you have one thread polling
  * and then a pool of threads simultaneously working on actions.
  * <br/>
  * Example:
  * <code>
  * runOne() {
  * task := worker.pollForWork()
  * if (task != null) {
  * threadPool.submit(() -> { worker.doWork(task) })
  * }
  * </code>
  *
  * @param domain             The domain of the workflow: <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-domain.html">AWS docs</a>
  *                           as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
  * @param taskList           If you execute the same workflow in different environments, use different task lists. Think of them as independent sets of actors working on the same logical workflow,
  *                           but in
  *                           different contexts (like production/qa/development/your machine).
  *                           <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-task-lists.html">AWS docs</a>
  *                           as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
  * @param swf                The SWF service client
  * @param inputParser        see InputParser
  * @param workflowDefinition see StepsDefinition
  * @tparam SSWFInput The type of the parsed workflow input
  * @tparam StepEnum  The enum containing workflow step definitions
  */
class StepActionWorker[SSWFInput, StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag](domain: String,
                                                                                             taskList: String,
                                                                                             swf: AmazonSimpleWorkflow,
                                                                                             inputParser: InputParser[SSWFInput],
                                                                                             workflowDefinition: WorkflowDefinition[SSWFInput, StepEnum],
                                                                                             log: Logger) {
  private[this] val identity = ManagementFactory.getRuntimeMXBean.getName

  /**
    * Poll SWF for actions (steps) that need to be executed.
    * A variety of exceptions are possible. Make sure your poller threads can't die without replacement.
    *
    * @return The action to be performed, if there is one. Null otherwise.
    */
  @Nullable
  def pollForWork(): ActivityTask = {
    val request = new PollForActivityTaskRequest()
       .withDomain(domain)
       .withTaskList(new TaskList().withName(taskList))
       .withIdentity(identity)

    val task =
      try {swf.pollForActivityTask(request)}
      catch {case t: Throwable => log.info(s"Exception polling for action. ${t.getClass}: ${t.getMessage}"); throw t}

    if (task.getTaskToken != null) {
      task
    } else {
      null
    }
  }

  /**
    * Execute the action using the logic in the workflowDefinition.
    * A variety of exceptions are possible. Make sure your poller threads can't die without replacement.
    *
    * @param activityTask The task to execute
    * @return The ActivityTaskCompleted response we sent to SWF
    */
  def doWork(@NotNull activityTask: ActivityTask): RespondActivityTaskCompletedRequest = {
    require(activityTask != null, "activityTask must not be null")


    val task = Enum.valueOf(classTag[StepEnum].runtimeClass.asInstanceOf[Class[StepEnum]], activityTask.getActivityId)
    val (stepInput, wfInput) = unpackInput(inputParser)(activityTask.getInput)

    log.debug(s"[${activityTask.getTaskToken}] starting[$task] stepInput[$stepInput]")

    val heartbeatCallback = new HeartbeatCallback {
      /**
        * Report liveness and progress. Calling this method resets the timeout for the step. Response `true` if cancellation is requested.
        *
        * @param progressMessage Report any information about your progress.
        * @return `true` if cancellation is requested.
        */
      override def checkIn(progressMessage: String): Boolean = {
        val heartbeat: ActivityTaskStatus =
          try {
            swf.recordActivityTaskHeartbeat(
              new RecordActivityTaskHeartbeatRequest()
                 .withTaskToken(activityTask.getTaskToken)
                 .withDetails(progressMessage)
            )
          } catch {
            case t: Throwable =>
              log.error(s"[${activityTask.getTaskToken}] Exception recording heartbeat", t)
              throw t
          }
        log.debug(s"[${activityTask.getTaskToken}] heartbeat. progress[$progressMessage] cancel[${heartbeat.getCancelRequested}]")

        heartbeat.getCancelRequested
      }
    }

    val result =
      try {workflowDefinition.act(task, wfInput, stepInput, heartbeatCallback, activityTask.getWorkflowExecution)}
      catch {case t: Throwable => log.error(s"[${activityTask.getTaskToken}] Exception in act()", t); throw t}
    log.debug(s"[${activityTask.getTaskToken}] action result: $result")

    val serializedStepResult: String = StepResult.serialize(result)
    require(serializedStepResult.length <= 32768, s"The serialized step result was too long. " +
       s"This is likely due to your message being too long or waiting on too many signals, etc.: [$serializedStepResult]")
    val response: RespondActivityTaskCompletedRequest =
      new RespondActivityTaskCompletedRequest()
         .withResult(serializedStepResult)
         .withTaskToken(activityTask.getTaskToken)

    try {
      swf.respondActivityTaskCompleted(response)
    } catch {
      case t: Throwable =>
        log.error(s"[${activityTask.getTaskToken}] Exception reporting action result to SWF", t)
        throw t
    }
    response
  }
}
