package com.bazaarvoice.sswf.model.history

import com.bazaarvoice.sswf.WorkflowStep
import com.bazaarvoice.sswf.model.{DefinedStep, ScheduledStep}
import org.joda.time.{DateTime, Duration}

import scala.reflect.ClassTag


/**
 * An individual event in the history
 * @param canonicalId An identifier for the logical step. This is not unique in the history. All events related to a particular activity will share the same id.
 * @param event The particular kind of event it is.
 * @param result The result of the event (SUCCESS, FAILED, TIMED_OUT, etc, etc).
 * @param start The start time of the individual activity (time since it was scheduled).
 * @param end The end time of the individual activity (if it has completed, timed out, failed, etc.)
 * @param cumulativeActivityTime The amount of time since the logical step was first scheduled.
 * @tparam StepEnum The type of the workflow step
 */
case class StepEvent[StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag](canonicalId: Long,
                                                                                uniqueId: Long,
                                                                                event: Either[ScheduledStep[StepEnum], String],
                                                                                result: String,
                                                                                start: DateTime,
                                                                                end: Option[DateTime],
                                                                                cumulativeActivityTime: Duration)

/**
 * A roll-up of the history and current state of a workflow.
 * @param input The input to the workflow
 * @param events A list (in order) of the parsed history of the workflow execution.
 * @param firedTimers Any outstanding timers which have not been handled.
 * @tparam SSWFInput The type of the workflow input.
 * @tparam StepEnum The type of the workflow steps
 */
case class StepsHistory[SSWFInput, StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag](input: SSWFInput,
                                                                                              events: java.util.List[StepEvent[StepEnum]],
                                                                                              firedTimers: java.util.Set[DefinedStep[StepEnum]],
                                                                                              expiredSignals: java.util.Set[String]
                                                                                             )
