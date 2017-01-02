package com.bazaarvoice.sswf.service

import com.bazaarvoice.sswf.WorkflowStep

object util {
  // Observation: the version number doesn't have to be monotonically increasing, it's really just a way to
  // fingerprint a certain step configuration so we can be sure we are executing the step we intend to.
  // So instead of a real version number, we use the timeout. If that changes, the step is logically different.
  private[service] def stepToVersion[StepEnum <: (Enum[StepEnum] with WorkflowStep)](step: StepEnum): String =
    s"${step.timeoutSeconds}" + ".hbtm" /*feature: consolidate timeouts to just heartbeat*/

}
