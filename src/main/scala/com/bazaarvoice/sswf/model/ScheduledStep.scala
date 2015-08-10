package com.bazaarvoice.sswf.model

import com.bazaarvoice.sswf.WorkflowStep

case class ScheduledStep[StepEnum <: (Enum[StepEnum] with WorkflowStep)](step: StepEnum, stepInput: Option[String] = None) {
  def this(step: StepEnum) = this(step, None)
  def this(step: StepEnum, stepInput: String) = this(step, Some(stepInput))

  stepInput.foreach(input => require(!input.contains("\0"), "input may not contain a null byte."))
}
