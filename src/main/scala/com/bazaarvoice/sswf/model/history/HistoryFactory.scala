package com.bazaarvoice.sswf.model.history

import java.util.Collections
import java.{lang, util}

import com.amazonaws.services.simpleworkflow.model.EventType._
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model.result._
import com.bazaarvoice.sswf.model.{DefinedStep, ScheduledStep, SleepStep}
import com.bazaarvoice.sswf.util._
import com.bazaarvoice.sswf.{InputParser, WorkflowStep}
import org.joda.time.{DateTime, Duration}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.reflect._

/**
  * static factory for parsing SWF histories into our <code>StepsHistory</code>.
  */
object HistoryFactory {
  def from[SSWFInput, StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag](swfHistory: List[HistoryEvent],
                                                                                 inputParser: InputParser[SSWFInput]): StepsHistory[SSWFInput, StepEnum] = {

    def enumFromName(name: String): StepEnum = Enum.valueOf(classTag[StepEnum].runtimeClass.asInstanceOf[Class[StepEnum]], name)

    def filterStarts(s: List[StepEvent[StepEnum]]): List[StepEvent[StepEnum]] = s match {
      case Nil | _ :: Nil                                                     =>
        s
      case first :: second :: rest if first.canonicalId == second.canonicalId =>
        filterStarts(second :: rest)
      case first :: rest                                                      =>
        first :: filterStarts(rest)
    }

    val steps = mutable.Map[Long, HistoryEvent]()
    val scheduledSteps = mutable.Map[Long, ScheduledStep[StepEnum]]()
    var workflowStartId: Long = -1
    var workflowStartTime: DateTime = null
    var input: String = null

    val startedTimers = mutable.Map[String, HistoryEvent]()
    val firedTimers = mutable.Map[String, Long]()
    val cancelledTimers = mutable.Map[String, Long]()

    // track the cumulative step time
    var currentStep: ScheduledStep[StepEnum] = null
    var currentStepStart: DateTime = null
    var invocations: Int = 0
    var workflowCancelRequested: Boolean = false

    var completedStepCounts = Map[StepEnum, Int]().withDefaultValue(0)

    var expiredSignals = Set[String]()

    val eventsRaw =
      for {h <- swfHistory} yield {
        EventType.fromValue(h.getEventType) match {
          // Workflow state transitions =======================================================================
          case WorkflowExecutionStarted         =>
            workflowStartTime = new DateTime(h.getEventTimestamp)
            workflowStartId = h.getEventId
            input = h.getWorkflowExecutionStartedEventAttributes.getInput
            Some(StepEvent[StepEnum](workflowStartId, h.getEventId, Right(WorkflowEventToken), "STARTED", workflowStartTime, None, Duration.ZERO, 0))
          case WorkflowExecutionCompleted       =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](workflowStartId, h.getEventId, Right(WorkflowEventToken), "SUCCESS", workflowStartTime, Some(dt), new Duration(workflowStartTime, dt), 0))
          case WorkflowExecutionFailed          =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](workflowStartId, h.getEventId, Right(WorkflowEventToken), "FAILED", workflowStartTime, Some(dt), new Duration(workflowStartTime, dt), 0))
          case WorkflowExecutionCanceled        =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](workflowStartId, h.getEventId, Right(WorkflowEventToken), "CANCELLED", workflowStartTime, Some(dt), new Duration(workflowStartTime, dt), 0))
          case WorkflowExecutionTerminated      =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](workflowStartId, h.getEventId, Right(WorkflowEventToken), "TERMINATED", workflowStartTime, Some(dt), new Duration(workflowStartTime, dt), 0))
          case WorkflowExecutionTimedOut        =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](workflowStartId, h.getEventId, Right(WorkflowEventToken), "TIMED_OUT", workflowStartTime, Some(dt), new Duration(workflowStartTime, dt), 0))
          case WorkflowExecutionCancelRequested =>
            // just flag the workflow as cancelled, and the decision worker will figure out what to do with it.
            workflowCancelRequested = true
            None

          // User-defined activity state transitions =======================================================================
          case ActivityTaskScheduled =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskScheduledEventAttributes = h.getActivityTaskScheduledEventAttributes
            val id: String = attributes.getActivityId
            val (stepInput, _) = unpackInput(inputParser)(attributes.getInput)
            val scheduledStep = DefinedStep(enumFromName(id), stepInput)
            if (currentStep == null) {
              currentStep = scheduledStep
              currentStepStart = new DateTime(h.getEventTimestamp)
              invocations = 0
            }
            scheduledSteps.put(h.getEventId, scheduledStep)

            val eventStart: DateTime = new DateTime(h.getEventTimestamp)

            Some(StepEvent[StepEnum](h.getEventId, h.getEventId, Left(scheduledStep), "SCHEDULED", eventStart, None, Duration.ZERO, invocations))

          case ActivityTaskStarted =>
            steps.put(h.getEventId, h)
            val eventId: lang.Long = h.getActivityTaskStartedEventAttributes.getScheduledEventId
            val eventStart: DateTime = new DateTime(h.getEventTimestamp)
            val scheduledStep: ScheduledStep[StepEnum] = scheduledSteps(eventId)

            assert(scheduledStep isSameStepAs currentStep, s"scheduledStep[$scheduledStep] !sameStepAs currentStep[$currentStep]")
            invocations += 1

            Some(StepEvent[StepEnum](eventId, h.getEventId, Left(scheduledStep), "STARTED", eventStart, None, new Duration(currentStepStart, eventStart), invocations))

          case ActivityTaskCompleted =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskCompletedEventAttributes = h.getActivityTaskCompletedEventAttributes
            val eventId: Long = attributes.getScheduledEventId
            val scheduledStep = scheduledSteps(eventId).asInstanceOf[DefinedStep[StepEnum]]
            val result: StepResult = StepResult.deserialize(attributes.getResult)
            val eventStart: DateTime = new DateTime(steps(attributes.getStartedEventId).getEventTimestamp)
            val endTime: DateTime = new DateTime(h.getEventTimestamp)
            completedStepCounts = completedStepCounts + (scheduledStep.step -> (completedStepCounts(scheduledStep.step) + 1))

            assert(scheduledStep isSameStepAs currentStep, s"scheduledStep[$scheduledStep] !sameStepAs currentStep[$currentStep]")

            val stepEventResult = Some(StepEvent[StepEnum](eventId, h.getEventId, Left(scheduledStep), StepResult.serialize(result), eventStart, Some(endTime), new Duration(currentStepStart,
              endTime), invocations))

            if (!result.isInProgress) {
              currentStep = null
              currentStepStart = null
              invocations = 0
            }
            stepEventResult

          case ActivityTaskTimedOut =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskTimedOutEventAttributes = h.getActivityTaskTimedOutEventAttributes
            val eventId: Long = attributes.getScheduledEventId
            val timeoutType: String = attributes.getTimeoutType
            val result = TimedOut(timeoutType, Option(attributes.getDetails))

            val eventStart =
              if (timeoutType == "SCHEDULE_TO_START") {
                new DateTime(steps(eventId).getEventTimestamp)
              } else {
                new DateTime(steps(attributes.getStartedEventId).getEventTimestamp)
              }
            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)
            val scheduledStep: ScheduledStep[StepEnum] = scheduledSteps(eventId)

            assert(scheduledStep isSameStepAs currentStep, s"scheduledStep[$scheduledStep] !sameStepAs currentStep[$currentStep]")
            Some(StepEvent[StepEnum](eventId, h.getEventId, Left(scheduledStep), StepResult.serialize(result), eventStart, Some(endTime), new Duration(currentStepStart, endTime), invocations))

          case ActivityTaskFailed =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskFailedEventAttributes = h.getActivityTaskFailedEventAttributes
            val eventId: Long = attributes.getScheduledEventId
            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)
            val result = Failed(Some(s"${attributes.getReason}:${attributes.getDetails}"))
            val scheduledStep: ScheduledStep[StepEnum] = scheduledSteps(eventId)

            assert(scheduledStep isSameStepAs currentStep, s"scheduledStep[$scheduledStep] !sameStepAs currentStep[$currentStep]")

            val stepEventResult = Some(StepEvent[StepEnum](eventId, h.getEventId, Left(scheduledStep), StepResult.serialize(result), workflowStartTime, Some(endTime), new Duration(currentStepStart,
              endTime), invocations))

            currentStep = null
            currentStepStart = null

            stepEventResult

          // Special activity state transitions =======================================================================

          case TimerStarted if h.getTimerStartedEventAttributes.getControl == "SleepStep" =>
            val eventId = h.getEventId
            val attributes = h.getTimerStartedEventAttributes
            val timerId = attributes.getTimerId
            val scheduledStep = SleepStep[StepEnum](attributes.getStartToFireTimeout.toInt)
            if (currentStep != scheduledStep) {
              currentStep = scheduledStep
              currentStepStart = new DateTime(h.getEventTimestamp)
              invocations = 1
            }
            startedTimers.put(timerId, h)

            assert(scheduledStep == currentStep, s"scheduledStep[$scheduledStep] != currentStep[$currentStep]")
            Some(StepEvent[StepEnum](eventId, eventId, Left(scheduledStep), "STARTED", currentStepStart, None, Duration.ZERO, invocations))

          case TimerFired if startedTimers(h.getTimerFiredEventAttributes.getTimerId).getTimerStartedEventAttributes.getControl == "SleepStep" =>
            val eventId = h.getEventId
            val attributes = h.getTimerFiredEventAttributes
            val timerId = attributes.getTimerId
            val canonicalId = startedTimers.getOrElse(timerId, h).getEventId
            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)

            val scheduledStep = SleepStep[StepEnum](startedTimers(attributes.getTimerId).getTimerStartedEventAttributes.getStartToFireTimeout.toInt)

            val result = Success(Some("Sleep finished"))
            val stepEventResult = Some(StepEvent[StepEnum](canonicalId, eventId, Left(scheduledStep), StepResult.serialize(result), currentStepStart, Some(endTime), new Duration(currentStepStart,
              endTime), invocations))

            assert(scheduledStep isSameStepAs currentStep, s"scheduledStep[$scheduledStep] !sameStepAs currentStep[$currentStep]")

            currentStep = null
            currentStepStart = null
            invocations = 0

            stepEventResult

          case TimerCanceled if startedTimers(h.getTimerCanceledEventAttributes.getTimerId).getTimerStartedEventAttributes.getControl == "SleepStep" =>
            val eventId = h.getEventId
            val attributes = h.getTimerCanceledEventAttributes
            val timerId = attributes.getTimerId
            val canonicalId = startedTimers.getOrElse(timerId, h).getEventId
            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)

            val scheduledStep = SleepStep[StepEnum](startedTimers(attributes.getTimerId).getTimerStartedEventAttributes.getStartToFireTimeout.toInt)

            val result = Cancelled(Some("Sleep cancelled"))

            assert(scheduledStep isSameStepAs currentStep, s"scheduledStep[$scheduledStep] !sameStepAs currentStep[$currentStep]")
            Some(StepEvent[StepEnum](canonicalId, eventId, Left(scheduledStep), StepResult.serialize(result), currentStepStart, Some(endTime), new Duration(currentStepStart, endTime), invocations))

          // Workflow Signals ==================================================================================
          case TimerStarted if h.getTimerStartedEventAttributes.getControl.startsWith("WaitForSignals:") =>
            // This is a byproduct of starting the signal wait. We don't really want it to be part of the workflow history.
            val attributes = h.getTimerStartedEventAttributes
            val timerId = attributes.getTimerId
            startedTimers.put(timerId, h)
            None

          case TimerFired if startedTimers(h.getTimerFiredEventAttributes.getTimerId).getTimerStartedEventAttributes.getControl.startsWith("WaitForSignals:") =>
            val control = startedTimers(h.getTimerFiredEventAttributes.getTimerId).getTimerStartedEventAttributes.getControl
            val _ :: signals = control.split(":").toList
            expiredSignals = expiredSignals ++ signals

            // Firing of this timer may indicate a WAIT step failure, or it might be no problem.
            // We don't know which it is right now, so just keep it out of the history.
            None

          case TimerCanceled if startedTimers(h.getTimerCanceledEventAttributes.getTimerId).getTimerStartedEventAttributes.getControl.startsWith("WaitForSignals:") =>
            // cancelled timers are irrelevant for our purposes here.
            None

          case WorkflowExecutionSignaled =>
            val attributes = h.getWorkflowExecutionSignaledEventAttributes
            val signalName: String = attributes.getSignalName

            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)

            Some(StepEvent[StepEnum](h.getEventId, h.getEventId, Right(s"SIGNAL:$signalName"), "RECEIVED", currentStepStart, Some(endTime), new Duration(currentStepStart, endTime), invocations))


          // Retry timer state transitions =======================================================================
          case TimerStarted =>
            val attributes = h.getTimerStartedEventAttributes
            val timerId = attributes.getTimerId
            startedTimers.put(timerId, h)
            None

          case TimerFired =>
            val attributes = h.getTimerFiredEventAttributes
            val timerId = attributes.getTimerId
            //            val scheduledStepStr = startedTimers(timerId).getTimerStartedEventAttributes.getControl
            firedTimers.put(timerId, h.getEventId)
            None

          case TimerCanceled =>
            val attributes = h.getTimerCanceledEventAttributes
            val timerId = attributes.getTimerId
            //            val scheduledStepStr = startedTimers(timerId).getTimerStartedEventAttributes.getControl
            cancelledTimers.put(timerId, h.getEventId)
            None

          // There are many types of WF events which we simply do not care about ===============================
          case _ =>
            None
        }
      }

    val events: List[StepEvent[StepEnum]] = eventsRaw.filter(_.isDefined).map(_.get)

    val firedSteps =
      for {
        (timerId, firedEventId) <- firedTimers.toList
        if !cancelledTimers.contains(timerId)

        scheduledStepStr = startedTimers(timerId).getTimerStartedEventAttributes.getControl
        (stepName, stepInput) = unpackTimer(scheduledStepStr)
        definedStep = DefinedStep(enumFromName(stepName), stepInput)

        handled = events.exists(stepEvent => handledEventFilter(firedEventId, definedStep, stepEvent))
        if !handled
      } yield {
        definedStep
      }


    val compactedHistory: util.List[StepEvent[StepEnum]] = filterStarts(events)

    StepsHistory[SSWFInput, StepEnum](
      inputParser.deserialize(input),
      Collections.unmodifiableList(compactedHistory),
      Collections.unmodifiableSet(new util.HashSet[DefinedStep[StepEnum]](firedSteps)),
      Collections.unmodifiableSet(expiredSignals),
      workflowCancelRequested
    )
  }

  def handledEventFilter[StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag, SSWFInput](firedEventId: Long,
                                                                                               scheduledStep: ScheduledStep[StepEnum],
                                                                                               stepEvent: StepEvent[StepEnum]): Boolean = {
    val youngEnough: Boolean = stepEvent.uniqueId > firedEventId
    val rightStep: Boolean = stepEvent.event.fold(_ == scheduledStep, _ => false)
    youngEnough && rightStep
  }
}
