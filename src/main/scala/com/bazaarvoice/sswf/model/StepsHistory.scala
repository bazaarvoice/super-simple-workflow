package com.bazaarvoice.sswf.model

import java.{util, lang}
import java.util.Collections

import com.amazonaws.services.simpleworkflow.model.EventType._
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.StepEventState._
import com.bazaarvoice.sswf.{InputParser, SSWFStep, StepEventState}
import org.joda.time.{DateTime, Duration}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

object WorkflowEventToken {
  override def toString = "WORKFLOW"
}

case class StepEvent[StepEnum <: (Enum[StepEnum] with SSWFStep) : ClassTag](id: Long,
                                                                            event: Either[StepEnum, WorkflowEventToken.type],
                                                                            result: String,
                                                                            state: StepEventState,
                                                                            start: DateTime,
                                                                            end: Option[DateTime],
                                                                            cumulativeActivityTime: Duration)

case class StepsHistory[SSWFInput, StepEnum <: (Enum[StepEnum] with SSWFStep) : ClassTag](input: SSWFInput, events: java.util.List[StepEvent[StepEnum]], firedTimers: java.util.Set[StepEnum])

object HistoryFactory {
  def from[SSWFInput, StepEnum <: (Enum[StepEnum] with SSWFStep) : ClassTag](swfHistory: List[HistoryEvent],
                                                                             inputParser: InputParser[SSWFInput]): StepsHistory[SSWFInput, StepEnum] = {

    def enumFromName(name: String): StepEnum = Enum.valueOf(classTag[StepEnum].runtimeClass.asInstanceOf[Class[StepEnum]], name)

    def filterStarts(s: List[StepEvent[StepEnum]]): List[StepEvent[StepEnum]] = s match {
      case Nil | _ :: Nil                                   =>
        s
      case first :: second :: rest if first.id == second.id =>
        filterStarts(rest)
      case first :: rest                                    =>
        first :: filterStarts(rest)
    }

    val steps = mutable.Map[Long, HistoryEvent]()
    var startTime: DateTime = null
    val startTimes = mutable.Map[String, DateTime]()
    var input: String = null

    val startedTimers = mutable.Map[String, HistoryEvent]()
    val firedTimers = mutable.Set[String]()
    val cancelledTimers = mutable.Set[String]()
    var completedStepCounts = Map[StepEnum, Int]().withDefaultValue(0)

    val eventsRaw =
      for {h <- swfHistory} yield {
        EventType.fromValue(h.getEventType) match {
          case WorkflowExecutionStarted   =>
            startTime = new DateTime(h.getEventTimestamp)
            input = h.getWorkflowExecutionStartedEventAttributes.getInput
            Some(StepEvent[StepEnum](h.getEventId, Right(WorkflowEventToken), "STARTED", STARTED, startTime, None, new Duration(startTime, startTime)))
          case WorkflowExecutionCompleted =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](h.getEventId, Right(WorkflowEventToken), "SUCCESS", COMPLETE, startTime, Some(dt), new Duration(startTime, dt)))
          case WorkflowExecutionFailed    =>
            val dt = new DateTime(h.getEventTimestamp)
            Some(StepEvent[StepEnum](h.getEventId, Right(WorkflowEventToken), "FAILED", FAILED, startTime, Some(dt), new Duration(startTime, dt)))
          case ActivityTaskScheduled      =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskScheduledEventAttributes = h.getActivityTaskScheduledEventAttributes
            val id: String = attributes.getActivityId
            val eventStart: DateTime = new DateTime(h.getEventTimestamp)
            if (!startTimes.contains(id)) {
              startTimes.put(id, new DateTime(h.getEventTimestamp))
            }
            val stepStart: DateTime = new DateTime(startTimes(id))
            Some(StepEvent[StepEnum](h.getEventId, Left(enumFromName(id)), "SCHEDULED", SCHEDULED, eventStart, None, new Duration(stepStart, eventStart)))
          case ActivityTaskStarted        =>
            steps.put(h.getEventId, h)
            val eventId: lang.Long = h.getActivityTaskStartedEventAttributes.getScheduledEventId
            val id: String = steps(eventId).getActivityTaskScheduledEventAttributes.getActivityId
            val eventStart: DateTime = new DateTime(h.getEventTimestamp)
            val stepStart: DateTime = new DateTime(startTimes(id))
            Some(StepEvent[StepEnum](eventId, Left(enumFromName(id)), "STARTED", STARTED, eventStart, None, new Duration(stepStart, eventStart)))
          case ActivityTaskCompleted      =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskCompletedEventAttributes = h.getActivityTaskCompletedEventAttributes
            val eventId: Long = attributes.getScheduledEventId
            val id: String = steps(eventId).getActivityTaskScheduledEventAttributes.getActivityId
            val result: String = attributes.getResult
            val eventStart: DateTime = new DateTime(steps(attributes.getStartedEventId).getEventTimestamp)
            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)
            val stepStart: DateTime = new DateTime(startTimes(id))

            val enum: StepEnum = enumFromName(id)
            completedStepCounts = completedStepCounts + (enum -> (completedStepCounts(enum) + 1))
            Some(StepEvent[StepEnum](eventId, Left(enum), result, COMPLETE, eventStart, Some(endTime), new Duration(stepStart, endTime)))
          case ActivityTaskTimedOut       =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskTimedOutEventAttributes = h.getActivityTaskTimedOutEventAttributes
            val eventId: Long = attributes.getScheduledEventId
            val id: String = steps(eventId).getActivityTaskScheduledEventAttributes.getActivityId
            val timeoutType: String = attributes.getTimeoutType
            val eventStart =
              if (timeoutType == "SCHEDULE_TO_START") {
                new DateTime(steps(eventId).getEventTimestamp)
              } else {
                new DateTime(steps(attributes.getStartedEventId).getEventTimestamp)
              }
            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)
            val stepStart: DateTime = new DateTime(startTimes(id))
            Some(StepEvent[StepEnum](eventId, Left(enumFromName(id)), s"TIMED_OUT: $timeoutType", TIMED_OUT, eventStart, Some(endTime), new Duration(stepStart, endTime)))
          case ActivityTaskFailed         =>
            steps.put(h.getEventId, h)
            val attributes: ActivityTaskFailedEventAttributes = h.getActivityTaskFailedEventAttributes
            val eventId: Long = attributes.getScheduledEventId
            val id: String = steps(eventId).getActivityTaskScheduledEventAttributes.getActivityId
            val endTime: DateTime = new DateTime(h.getEventTimestamp.getTime)
            val stepStart: DateTime = new DateTime(startTimes(id))
            Some(StepEvent[StepEnum](eventId, Left(enumFromName(id)), s"${attributes.getReason}: ${attributes.getDetails}", FAILED, startTime, Some(endTime), new Duration(stepStart, endTime)))
          case TimerStarted               =>
            val attributes = h.getTimerFiredEventAttributes
            val timerId = attributes.getTimerId
            startedTimers.put(timerId, h)
            None
          case TimerFired                 =>
            val attributes = h.getTimerFiredEventAttributes
            val timerId = attributes.getTimerId
            firedTimers.add(timerId)
            None
          case TimerCanceled              =>
            val attributes = h.getTimerFiredEventAttributes
            val timerId = attributes.getTimerId
            cancelledTimers.add(timerId)
            None
          case _                          =>
            None
        }
      }

    val events: List[StepEvent[StepEnum]] = eventsRaw.filter(_.isDefined).map(_.get)

    // figure out which timers have fired and not yet been addressed:
    var firedTimerSteps = Map[StepEnum, Int]().withDefaultValue(0)

    for {
      timer <- firedTimers
      if !cancelledTimers.contains(timer)
    } yield {
      val stepId = startedTimers(timer).getTimerStartedEventAttributes.getControl
      val enum = enumFromName(stepId)
      firedTimerSteps = firedTimerSteps + (enum -> (firedTimerSteps(enum) + 1))
    }

    val firedSteps = for {
      (firedStep, firedCount) <- firedTimerSteps
      completedCount = completedStepCounts(firedStep)
      if firedCount == completedCount
    } yield {
        // when the step no longer needs to be scheduled, the completedCount will exceed the firedCount
        firedStep
      }

    val starts: util.List[StepEvent[StepEnum]] = filterStarts(events)

    StepsHistory[SSWFInput, StepEnum](inputParser.deserialize(input), Collections.unmodifiableList(starts), Collections.unmodifiableSet(new util.HashSet[StepEnum](firedSteps)))
  }

}
