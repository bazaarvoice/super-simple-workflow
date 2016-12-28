package com.bazaarvoice.sswf.model

import com.bazaarvoice.sswf.WorkflowStep

sealed trait ScheduledStep[StepEnum <: (Enum[StepEnum] with WorkflowStep)] {
  def isSameStepAs(other: ScheduledStep[StepEnum]): Boolean
}

case class SleepStep[StepEnum <: (Enum[StepEnum] with WorkflowStep)](sleepSeconds: Int) extends ScheduledStep[StepEnum] {
  override def isSameStepAs(other: ScheduledStep[StepEnum]): Boolean = this == other
}

case class DefinedStep[StepEnum <: (Enum[StepEnum] with WorkflowStep)](step: StepEnum, stepInput: StepInput = StepInput(None, None)) extends ScheduledStep[StepEnum] {
  def this(step: StepEnum) = this(step, StepInput(None, None))
  def this(step: StepEnum, stepInput: String) = this(step, StepInput(Some(stepInput), None))

  stepInput.stepInputString.foreach(input => require(!input.contains("\u0000"), "input may not contain a null byte."))

  def withoutResume = this.copy(stepInput = stepInput.copy(resumeProgress = None))

  override def isSameStepAs(other: ScheduledStep[StepEnum]): Boolean = other match {
    case s: SleepStep[StepEnum]             => false
    case DefinedStep(otherStep, otherInput) => step == otherStep && stepInput.stepInputString == otherInput.stepInputString
  }
}
