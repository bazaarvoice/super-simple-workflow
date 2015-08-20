package com.bazaarvoice.sswf.model.history

import java.util.Collections
import java.{lang, util}

import com.amazonaws.services.simpleworkflow.model.EventType._
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model.ScheduledStep
import com.bazaarvoice.sswf.model.result.{Failed, StepResult, TimedOut}
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
    var startId: Long = -1
    var startTime: DateTime = null
    val startTimes = mutable.Map[String, DateTime]()
    var input: String = null

    val startedTimers = mutable.Map[String, HistoryEvent]()
    val firedTimers = mutable.Map[String, Long]()
    val cancelledTimers = mutable.Map[String, Long]()
    var completedStepCounts = Map[StepEnum, Int]().withDefaultValue(0)

    val eventsRaw =
      for {h <- swfHistory} yield {
        EventType.fromValue(h.getEventType) match {
          case WorkflowExecutionStarted    =>
            startTime = new DateTime(h.getEventTimestamp)
            startId = h.getEventId
            input = h.getWorkflowExecutionStartedEventAttributes.getInput
            Some(StepEvent[StepEnum](startId, h.getEventId, Right(WorkflowEventToken), "STARTED", startTime, None, Duration.ZERO))
          case WorkflowExecutionCompleted  =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](startId, h.getEventId, Right(WorkflowEventToken), "SUCCESS", startTime, Some(dt), new Duration(startTime, dt)))
          case WorkflowExecutionFailed     =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](startId, h.getEventId, Right(WorkflowEventToken), "FAILED", startTime, Some(dt), new Duration(startTime, dt)))
          case WorkflowExecutionCanceled   =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](startId, h.getEventId, Right(WorkflowEventToken), "CANCELED", startTime, Some(dt), new Duration(startTime, dt)))
          case WorkflowExecutionTerminated =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](startId, h.getEventId, Right(WorkflowEventToken), "TERMINATED", startTime, Some(dt), new Duration(startTime, dt)))
          case WorkflowExecutionTimedOut   =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](startId, h.getEventId, Right(WorkflowEventToken), "TIMED_OUT", startTime, Some(dt), new Duration(startTime, dt)))

          case ActivityTaskScheduled =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskScheduledEventAttributes = h.getActivityTaskScheduledEventAttributes
            val id: String = attributes.getActivityId
            val (stepInput, _) = unpackInput(inputParser)(attributes.getInput)

            val scheduledStep = ScheduledStep(enumFromName(id), stepInput)
            scheduledSteps.put(h.getEventId, scheduledStep)

            val eventStart: DateTime = new DateTime(h.getEventTimestamp)

            if (!startTimes.contains(id)) {
              startTimes.put(id, new DateTime(h.getEventTimestamp))
            }

            val stepStart: DateTime = new DateTime(startTimes(id))

            Some(StepEvent[StepEnum](h.getEventId, h.getEventId, Left(scheduledStep), "SCHEDULED", eventStart, None, new Duration(stepStart, eventStart)))
          case ActivityTaskStarted   =>
            steps.put(h.getEventId, h)
            val eventId: lang.Long = h.getActivityTaskStartedEventAttributes.getScheduledEventId
            val id: String = steps(eventId).getActivityTaskScheduledEventAttributes.getActivityId
            val eventStart: DateTime = new DateTime(h.getEventTimestamp)
            val stepStart: DateTime = new DateTime(startTimes(id))
            Some(StepEvent[StepEnum](eventId, h.getEventId, Left(scheduledSteps(eventId)), "STARTED", eventStart, None, new Duration(stepStart, eventStart)))
          case ActivityTaskCompleted =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskCompletedEventAttributes = h.getActivityTaskCompletedEventAttributes
            val eventId: Long = attributes.getScheduledEventId
            val scheduledStep = scheduledSteps(eventId)
            val id: String = steps(eventId).getActivityTaskScheduledEventAttributes.getActivityId
            val result: StepResult = StepResult.deserialize(attributes.getResult)
            val eventStart: DateTime = new DateTime(steps(attributes.getStartedEventId).getEventTimestamp)
            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)
            val stepStart: DateTime = new DateTime(startTimes(id))

            completedStepCounts = completedStepCounts + (scheduledStep.step -> (completedStepCounts(scheduledStep.step) + 1))
            Some(StepEvent[StepEnum](eventId, h.getEventId, Left(scheduledStep), StepResult.serialize(result), eventStart, Some(endTime), new Duration(stepStart, endTime)))
          case ActivityTaskTimedOut  =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskTimedOutEventAttributes = h.getActivityTaskTimedOutEventAttributes
            val eventId: Long = attributes.getScheduledEventId
            val id: String = steps(eventId).getActivityTaskScheduledEventAttributes.getActivityId
            val timeoutType: String = attributes.getTimeoutType
            val result: TimedOut = TimedOut(timeoutType)
            val eventStart =
              if (timeoutType == "SCHEDULE_TO_START") {
                new DateTime(steps(eventId).getEventTimestamp)
              } else {
                new DateTime(steps(attributes.getStartedEventId).getEventTimestamp)
              }
            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)
            val stepStart: DateTime = new DateTime(startTimes(id))
            Some(StepEvent[StepEnum](eventId, h.getEventId, Left(scheduledSteps(eventId)), StepResult.serialize(result), eventStart, Some(endTime), new Duration(stepStart, endTime)))
          case ActivityTaskFailed    =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskFailedEventAttributes = h.getActivityTaskFailedEventAttributes
            val eventId: Long = attributes.getScheduledEventId
            val id: String = steps(eventId).getActivityTaskScheduledEventAttributes.getActivityId
            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)
            val stepStart: DateTime = new DateTime(startTimes(id))
            val result = Failed(s"${attributes.getReason}:${attributes.getDetails}")
            Some(StepEvent[StepEnum](eventId, h.getEventId, Left(scheduledSteps(eventId)), StepResult.serialize(result), startTime, Some(endTime), new Duration(stepStart, endTime)))
          case TimerStarted          =>
            val attributes = h.getTimerStartedEventAttributes
            val timerId = attributes.getTimerId
            startedTimers.put(timerId, h)
            None
          case TimerFired            =>
            val attributes = h.getTimerFiredEventAttributes
            val timerId = attributes.getTimerId
            firedTimers.put(timerId, h.getEventId)
            None
          case TimerCanceled         =>
            val attributes = h.getTimerCanceledEventAttributes
            val timerId = attributes.getTimerId
            cancelledTimers.put(timerId, h.getEventId)
            None
          case _                     => // There are many types of WF events which we simply do not care about.
            None
        }
      }

    val events: List[StepEvent[StepEnum]] = eventsRaw.filter(_.isDefined).map(_.get)

    val firedSteps =
      for {
        (timerId, firedEventId) <- firedTimers.toList
        if !cancelledTimers.contains(timerId)

        scheduledStepStr = startedTimers(timerId).getTimerStartedEventAttributes.getControl
        scheduledStep = if (scheduledStepStr.endsWith("\u0000\u0000")) {
          ScheduledStep(enumFromName(scheduledStepStr.takeWhile(_ != '\u0000')), None)
        } else {
          val name :: input :: Nil = scheduledStepStr.split("\u0000").toList
          ScheduledStep(enumFromName(name), Some(input))
        }
        handled = events.exists(stepEvent => handledEventFilter(firedEventId, scheduledStep, stepEvent))
        if !handled
      } yield {
        scheduledStep
      }


    val compactedHistory: util.List[StepEvent[StepEnum]] = filterStarts(events)

    StepsHistory[SSWFInput, StepEnum](inputParser.deserialize(input), Collections.unmodifiableList(compactedHistory), Collections.unmodifiableSet(new util.HashSet[ScheduledStep[StepEnum]]
    (firedSteps)))
  }

  def handledEventFilter[StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag, SSWFInput](firedEventId: Long,
                                                                                               scheduledStep: ScheduledStep[StepEnum],
                                                                                               stepEvent: StepEvent[StepEnum]): Boolean = {
    val oldEnough: Boolean = stepEvent.uniqueId > firedEventId
    val rightStep: Boolean = stepEvent.event.fold(_ == scheduledStep, _ => false)
    oldEnough && rightStep
  }
}
