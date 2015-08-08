package com.bazaarvoice.sswf.service

import java.lang.management.ManagementFactory
import java.util.UUID

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model._
import com.bazaarvoice.sswf.{InputParser, StepEventState, WorkflowDefinition, WorkflowStep}
import com.sun.istack.internal.{NotNull, Nullable}

import scala.collection.JavaConversions._
import scala.reflect._


/**
 * The worker class responsible for scheduling workflow actions. The class is built so that you can plug it in to a scheduled service of your choice.
 * First, call <code>pollForWork()</code> to determine if a decision needs to be made, and then call <code>doWork()</code> to make the decision.
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
 * {
 *
 *
 * @param domain The domain of the workflow: http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-domain.html
 * @param taskList The task list to work within: http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-task-lists.html
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
                                                                                               workflowDefinition: WorkflowDefinition[SSWFInput, StepEnum]) {
  private[this] val identity = ManagementFactory.getRuntimeMXBean.getName

  private[this] var _activityTypes: Map[String, Int] = null

  private[this] def activityTypes: Map[String, Int] = {
    if (_activityTypes == null) {
      _activityTypes = for ((activityName, types) <- com.bazaarvoice.sswf.aws.util.getActivityTypes(swf, domain).groupBy(_.getActivityType.getName)) yield {
          val maxVersion = types.map(_.getActivityType.getVersion.toInt).max
          activityName -> maxVersion
        }

      for (enum <- classTag[StepEnum].runtimeClass.asInstanceOf[Class[StepEnum]].getEnumConstants) {
        require(activityTypes.contains(enum.name), s"SWF is missing definition for $enum. Did you forget to call registerActivities()?")
      }
    }

    _activityTypes
  }


  /**
   * Find out if any workflows in our domain/taskList need decisions made.
   * @throws Throwable A variety of exceptions are possible. Make sure your poller threads can't die without replacement.
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
   * @param decisionTask The task from SWF. Must not be null!
   * @throws Throwable A variety of exceptions are possible. Make sure your worker threads can't die without replacement.
   * @return The decision that we sent to SWF
   */
  def makeDecision(@NotNull decisionTask: DecisionTask): RespondDecisionTaskCompletedRequest = {
    require(decisionTask != null, "decisionTask must not be null")

    val (newDecisionTask, events) = getFullHistory(decisionTask)
    val completedRequest: RespondDecisionTaskCompletedRequest = innerMakeDecision(newDecisionTask, events)

    swf.respondDecisionTaskCompleted(completedRequest)

    completedRequest
  }

  private[this] def innerMakeDecision(decisionTask: DecisionTask, events: List[HistoryEvent]): RespondDecisionTaskCompletedRequest = {
    val history: StepsHistory[SSWFInput, StepEnum] = HistoryFactory.from(events, inputParser)

    val input = history.input

    def respond(d: Decision): RespondDecisionTaskCompletedRequest =
      new RespondDecisionTaskCompletedRequest().withDecisions(List(d)).withTaskToken(decisionTask.getTaskToken)

    def schedule(activity: Option[StepEnum]) = activity match {
      case None       =>
        val message = "No more activities to schedule."
        workflowDefinition.onFinish(input, history, message)
        var attributes: CompleteWorkflowExecutionDecisionAttributes = new CompleteWorkflowExecutionDecisionAttributes
        attributes = attributes.withResult(message)

        new Decision().withDecisionType(DecisionType.CompleteWorkflowExecution).withCompleteWorkflowExecutionDecisionAttributes(attributes)
      case Some(step) =>
        val scheduleActivityTaskDecisionAttributes: ScheduleActivityTaskDecisionAttributes = new ScheduleActivityTaskDecisionAttributes()
           .withActivityId(step.name)
           .withActivityType(new ActivityType().withName(step.name).withVersion(activityTypes(step.name).toString))
           .withHeartbeatTimeout("NONE")
           .withTaskList(new TaskList().withName(taskList))
           .withInput(inputParser.serialize(input))

        new Decision()
           .withDecisionType(DecisionType.ScheduleActivityTask)
           .withScheduleActivityTaskDecisionAttributes(scheduleActivityTaskDecisionAttributes)
    }

    def fail(shortDescription: String, message: String) = {
      require(shortDescription.length < 256)
      val fullMessage = shortDescription + ": " + message
      workflowDefinition.onFail(input, history, fullMessage)
      val attributes: FailWorkflowExecutionDecisionAttributes = new FailWorkflowExecutionDecisionAttributes().withReason(fullMessage)

      new Decision().withDecisionType(DecisionType.FailWorkflowExecution).withFailWorkflowExecutionDecisionAttributes(attributes)
    }

    if (history.firedTimers.nonEmpty) {
      return respond(schedule(Some(history.firedTimers.head)))
    }

    val failedEvents: List[StepEvent[StepEnum]] = history.events.filter(e => e.event.isLeft && e.state == StepEventState.FAILED).toList

    if (failedEvents.nonEmpty) {
      respond(fail(s"failed ${failedEvents.size} activities", s"$failedEvents"))
    } else {
      for (pipe <- workflowDefinition.workflow(input)) {
        val lastOption: Option[StepEvent[StepEnum]] = history.events.filter(_.event == Left(pipe)).lastOption
        val result: Option[StepResult] = lastOption.map(l => {
          StepResult.deserialize(l.result)
        })
        result match {
          case None                => return respond(schedule(Some(pipe)))
          case Some(Failed(m))     => return respond(fail(s"Failed stage $pipe", Failed(m).toString))
          case Some(InProgress(m)) => return respond(waitRetry(pipe))
          case Some(TimedOut(m))   => return respond(waitRetry(pipe))
          case Some(Success(m))    => ()
        }
      }

      respond(schedule(None))
    }
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

  private[this] def waitRetry(retry: StepEnum) = new Decision().withDecisionType(DecisionType.StartTimer).withStartTimerDecisionAttributes(
    new StartTimerDecisionAttributes().withTimerId(UUID.randomUUID().toString).withControl(retry.name()).withStartToFireTimeout(retry.inProgressTimerSeconds.toString)
  )

}
