package com.bazaarvoice.sswf.model

import com.bazaarvoice.sswf.WorkflowStep

case class ScheduledStep[StepEnum <: (Enum[StepEnum] with WorkflowStep)](step: StepEnum, stepInput: StepInput = StepInput(None, None)) {
  def this(step: StepEnum) = this(step, StepInput(None, None))
  def this(step: StepEnum, stepInput: String) = this(step, StepInput(Some(stepInput), None))

  stepInput.stepInputString.foreach(input => require(!input.contains("\u0000"), "input may not contain a null byte."))

  def withoutResume = this.copy(stepInput = stepInput.copy(resumeProgress = None))
}
