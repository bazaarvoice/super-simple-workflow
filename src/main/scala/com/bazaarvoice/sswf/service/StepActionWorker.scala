package com.bazaarvoice.sswf.service

import java.lang.management.ManagementFactory

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model.StepResult
import com.bazaarvoice.sswf.{InputParser, WorkflowStep, WorkflowDefinition}
import com.sun.istack.internal.{NotNull, Nullable}

import scala.reflect._

class StepActionWorker[SSWFInput, StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag](domain: String,
                                                                                         taskList: String,
                                                                                         swf: AmazonSimpleWorkflow,
                                                                                         inputParser: InputParser[SSWFInput],
                                                                                         workflowDefinition: WorkflowDefinition[SSWFInput, StepEnum]) {
  private[this] val identity = ManagementFactory.getRuntimeMXBean.getName

  /**
   * Poll SWF for actions (steps) that need to be executed.
   * @throws Throwable A variety of exceptions are possible. Make sure your poller threads can't die without replacement.
   * @return The action to be performed, if there is one. Null otherwise.
   */
  @Nullable
  def pollForWork(): ActivityTask = {
    val request = new PollForActivityTaskRequest()
       .withDomain(domain)
       .withTaskList(new TaskList().withName(taskList))
       .withIdentity(identity)

    val task = swf.pollForActivityTask(request)

    if (task.getTaskToken != null) {
      task
    } else {
      null
    }
  }

  /**
   * Execute the action using the logic in the workflowDefinition.
   * @param activityTask The task to execute
   * @throws Throwable A variety of exceptions are possible. Make sure your poller threads can't die without replacement.
   * @return The ActivityTaskCompleted response we sent to SWF
   */
  def doWork(@NotNull activityTask: ActivityTask): RespondActivityTaskCompletedRequest = {
    require(activityTask != null, "activityTask must not be null")


    val task = Enum.valueOf(classTag[StepEnum].runtimeClass.asInstanceOf[Class[StepEnum]], activityTask.getActivityId)
    val input = inputParser.deserialize(activityTask.getInput)

    val result = workflowDefinition.act(task, input)

    val response: RespondActivityTaskCompletedRequest = new RespondActivityTaskCompletedRequest().withResult(StepResult.serialize(result)).withTaskToken(activityTask.getTaskToken)

    swf.respondActivityTaskCompleted(response)
    response
  }
}
