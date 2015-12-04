package com.bazaarvoice.sswf.service

import com.bazaarvoice.sswf.{InputParser, WorkflowStep}

object util {
  private[service] def stepToVersion[StepEnum <: (Enum[StepEnum] with WorkflowStep)](step: StepEnum): String = s"${step.startToFinishTimeoutSeconds}.${step.startToHeartbeatTimeoutSeconds}.${step.inProgressTimerSeconds}"

}
