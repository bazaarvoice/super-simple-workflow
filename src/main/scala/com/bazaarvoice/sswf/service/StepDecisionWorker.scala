package com.bazaarvoice.sswf.service

import java.lang.management.ManagementFactory
import java.util.UUID

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model.ScheduledStep
import com.bazaarvoice.sswf.model.history.{HistoryFactory, StepEvent, StepsHistory}
import com.bazaarvoice.sswf.model.result._
import com.bazaarvoice.sswf.util.{packInput, packTimer}
import com.bazaarvoice.sswf.{InputParser, Logger, WorkflowDefinition, WorkflowStep}
import com.sun.istack.internal.{NotNull, Nullable}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.reflect.ClassTag


/**
 * The worker class responsible for scheduling workflow actions. The class is built so that you can plug it in to a scheduled service of your choice.
 * First, call <code>pollForDecisionToMake()</code> to determine if a decision needs to be made, and then call <code>makeDecision()</code> to make the decision.
 * <br/>
 * You could obviously poll and work in the same thread, but remember that this worker can handle many concurrent workflows, so separating them lets you have one thread polling
 * and then a pool of threads simultaneously working on scheduling decisions.
 * <br/>
 * Example:
 * <code>
 * runOne() {
 * task := worker.pollForDecisionToMake()
 * if (task != null) {
 * threadPool.submit(() -> { worker.makeDecision(task) })
 * }
 * </code>
 *
 * @param domain The domain of the workflow: <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-domain.html">AWS docs</a>
 *               as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
 * @param taskList If you execute the same workflow in different environments, use different task lists. Think of them as independent sets of actors working on the same logical workflow, but in
 *                 different contexts (like production/qa/development/your machine).
 *                 <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-task-lists.html">AWS docs</a>
 *                 as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
 * @param swf The SWF service client
 * @param inputParser see InputParser
 * @param workflowDefinition see StepsDefinition
 * @tparam SSWFInput The type of the parsed workflow input
 * @tparam StepEnum The enum containing workflow step definitions
 */
class StepDecisionWorker[SSWFInput, StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag](domain: String,
                                                                                               taskList: String,
                                                                                               swf: AmazonSimpleWorkflow,
                                                                                               inputParser: InputParser[SSWFInput],
                                                                                               workflowDefinition: WorkflowDefinition[SSWFInput, StepEnum],
                                                                                               log: Logger) {
  private[this] val identity = ManagementFactory.getRuntimeMXBean.getName

  /**
   * Find out if any workflows in our domain/taskList need decisions made.
   * A variety of exceptions are possible. Make sure your poller threads can't die without replacement.
   * @return The decision task, if there is one. Null otherwise.
   */
  @Nullable
  def pollForDecisionsToMake(): DecisionTask = {
    val decisionTask: DecisionTask = swf.pollForDecisionTask(new PollForDecisionTaskRequest().withDomain(domain).withTaskList(new TaskList().withName(taskList)).withIdentity(identity))
    if (decisionTask.getTaskToken != null) {
      decisionTask
    } else {
      null
    }
  }

  /**
   * Given the context of a workflow execution, decide what needs to be done next.
   * A variety of exceptions are possible. Make sure your worker threads can't die without replacement.
   * @param decisionTask The task from SWF. Must not be null!
   * @return The decision that we sent to SWF
   */
  def makeDecision(@NotNull decisionTask: DecisionTask): RespondDecisionTaskCompletedRequest = {
    require(decisionTask != null, "decisionTask must not be null")

    log.debug(s"starting makeDecision for [${decisionTask.getStartedEventId}]")
    val (newDecisionTask, events) = getFullHistory(decisionTask)
    log.debug(s"get history for [${decisionTask.getStartedEventId}]")
    val completedRequest: RespondDecisionTaskCompletedRequest = innerMakeDecision(newDecisionTask, events)
    log.debug(s"made decision for [${decisionTask.getStartedEventId}]")

    swf.respondDecisionTaskCompleted(completedRequest)

    completedRequest
  }

  private[this] def innerMakeDecision(decisionTask: DecisionTask, events: List[HistoryEvent]): RespondDecisionTaskCompletedRequest = {
    log.debug(s"starting innerMakeDecision for [${decisionTask.getStartedEventId}]")
    val history: StepsHistory[SSWFInput, StepEnum] = HistoryFactory.from(events, inputParser)
    log.debug(s"got history for [${decisionTask.getStartedEventId}]")

    val input = history.input

    def respond(d: Decision): RespondDecisionTaskCompletedRequest =
      new RespondDecisionTaskCompletedRequest().withDecisions(List(d)).withTaskToken(decisionTask.getTaskToken)

    def resume(step: ScheduledStep[StepEnum]) = {
      val scheduleActivityTaskDecisionAttributes: ScheduleActivityTaskDecisionAttributes = new ScheduleActivityTaskDecisionAttributes()
         .withActivityId(step.step.name)
         .withActivityType(new ActivityType().withName(step.step.name).withVersion(util.stepToVersion(step.step)))
         .withTaskList(new TaskList().withName(taskList))
         .withInput(packInput(inputParser)(step.stepInput, input))

      new Decision()
         .withDecisionType(DecisionType.ScheduleActivityTask)
         .withScheduleActivityTaskDecisionAttributes(scheduleActivityTaskDecisionAttributes)
    }

    def schedule(activity: Option[ScheduledStep[StepEnum]]) = activity match {
      case None       =>
        val message = "No more activities to schedule."
        workflowDefinition.onFinish(decisionTask.getWorkflowExecution.getWorkflowId, decisionTask.getWorkflowExecution.getRunId, input, history, message)
        var attributes: CompleteWorkflowExecutionDecisionAttributes = new CompleteWorkflowExecutionDecisionAttributes
        attributes = attributes.withResult(message)

        new Decision().withDecisionType(DecisionType.CompleteWorkflowExecution).withCompleteWorkflowExecutionDecisionAttributes(attributes)
      case Some(step) =>
        val scheduleActivityTaskDecisionAttributes: ScheduleActivityTaskDecisionAttributes = new ScheduleActivityTaskDecisionAttributes()
           .withActivityId(step.step.name)
           .withActivityType(new ActivityType().withName(step.step.name).withVersion(util.stepToVersion(step.step)))
           .withTaskList(new TaskList().withName(taskList))
           .withInput(packInput(inputParser)(step.stepInput, input))

        new Decision()
           .withDecisionType(DecisionType.ScheduleActivityTask)
           .withScheduleActivityTaskDecisionAttributes(scheduleActivityTaskDecisionAttributes)
    }

    def fail(shortDescription: String, message: String) = {
      require(shortDescription.length < 256)
      val fullMessage = shortDescription + ": " + message
      workflowDefinition.onFail(decisionTask.getWorkflowExecution.getWorkflowId, decisionTask.getWorkflowExecution.getRunId, input, history, fullMessage)
      val attributes: FailWorkflowExecutionDecisionAttributes = new FailWorkflowExecutionDecisionAttributes().withReason(shortDescription).withDetails(fullMessage)

      new Decision().withDecisionType(DecisionType.FailWorkflowExecution).withFailWorkflowExecutionDecisionAttributes(attributes)
    }

    log.debug(s"checking for fired timers for [${decisionTask.getStartedEventId}]")
    if (history.firedTimers.nonEmpty) {
      return respond(schedule(Some(history.firedTimers.head)))
    }

    log.debug(s"finding final states for [${decisionTask.getStartedEventId}]")
    val finalStates = mutable.Buffer[StepEvent[StepEnum]]()
    for (e <- history.events if e.event.isLeft) {
      val result = StepResult.deserialize(e.result)
      if (finalStates.isEmpty) {
        finalStates.append(e)
      } else if (finalStates.last.event.left.get.withoutResume == e.event.left.get.withoutResume) {
        finalStates.remove(finalStates.length - 1)
        finalStates.append(e)
      } else {
        finalStates.append(e)
      }
    }

    log.debug(s"determining next step for [${decisionTask.getStartedEventId}]")
    val fsIt = finalStates.iterator
    for (step <- workflowDefinition.workflow(input)) {
      if (!fsIt.hasNext) {
        return respond(schedule(Some(step)))
      } else {
        val thisFS = fsIt.next()
        assert(thisFS.event.left.get.withoutResume == step, s"Did the workflow change? [${thisFS.event.left.get}] != [$step]")
        val result = StepResult.deserialize(thisFS.result)
        result match {
          case Failed(m)                         => return respond(fail(s"Failed stage $step", Failed(m).toString))
          case Cancelled(m)                      => return respond(fail(s"Cancelled stage $step", Cancelled(m).toString))
          case InProgress(m)                     => return respond(waitRetry(step))
          case TimedOut(timeoutType, resumeInfo) =>
            println("got: " + TimedOut(timeoutType, resumeInfo))
            return respond(resume(step.copy(stepInput = step.stepInput.copy(resumeProgress = resumeInfo))))
          case Success(m)                        => ()
        }
      }
    }

    respond(schedule(None))
  }

  private[this] def getFullHistory(decisionTask: DecisionTask): (DecisionTask, List[HistoryEvent]) = {
    var events = decisionTask.getEvents.toList
    var newDecisionTask = decisionTask
    while (newDecisionTask.getNextPageToken != null) {
      newDecisionTask = swf.pollForDecisionTask(
        new PollForDecisionTaskRequest()
           .withDomain(domain)
           .withTaskList(new TaskList().withName(taskList))
           .withIdentity(identity)
           .withNextPageToken(newDecisionTask.getNextPageToken)
      )
      events = events ::: newDecisionTask.getEvents.toList
    }
    (newDecisionTask, events)
  }

  private[this] def waitRetry(retry: ScheduledStep[StepEnum]) = new Decision().withDecisionType(DecisionType.StartTimer).withStartTimerDecisionAttributes(
    new StartTimerDecisionAttributes()
       .withTimerId(UUID.randomUUID().toString)
       .withControl(packTimer(retry.step.name, retry.stepInput))
       .withStartToFireTimeout(retry.step.inProgressTimerSeconds.toString)
  )

}
