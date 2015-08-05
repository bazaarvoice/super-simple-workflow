package com.bazaarvoice.sswf.service

import java.lang.management.ManagementFactory

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model.StepResult
import com.bazaarvoice.sswf.{InputParser, SSWFStep, WorkflowDefinition}
import com.sun.istack.internal.Nullable

import scala.reflect._

class StepActionWorker[SSWFInput, StepEnum <: (Enum[StepEnum] with SSWFStep) : ClassTag](domain: String,
                                                                                         taskList: String,
                                                                                         swf: AmazonSimpleWorkflow,
                                                                                         inputParser: InputParser[SSWFInput],
                                                                                         workflowDefinition: WorkflowDefinition[SSWFInput, StepEnum]) {
  private[this] val identity = ManagementFactory.getRuntimeMXBean.getName

  @Nullable
  def pollForWork(): ActivityTask = try {
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
  } catch {
    case t: Throwable =>
      System.err.println("Exception while polling for an action. Continuing...")
      t.printStackTrace(System.err)
      null
  }

  def doWork(activityTask: ActivityTask): Unit = try {
    if (activityTask == null) {return} // just being defensive, since we do return null from poll.


    val task = Enum.valueOf(classTag[StepEnum].runtimeClass.asInstanceOf[Class[StepEnum]], activityTask.getActivityId)
    val input = inputParser.deserialize(activityTask.getInput)

    val result = workflowDefinition.act(task, input)

    val response: RespondActivityTaskCompletedRequest = new RespondActivityTaskCompletedRequest().withResult(StepResult.toString(result)).withTaskToken(activityTask.getTaskToken)
    swf.respondActivityTaskCompleted(response)
  } catch {
    case t: Throwable =>
      System.err.println("Exception while executing an action. Continuing...")
      t.printStackTrace(System.err)
  }
}
