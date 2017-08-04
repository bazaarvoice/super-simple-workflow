package com.bazaarvoice.sswf

/**
 * Your Step Enum must implement/extend this
 */
trait WorkflowStep {
  /** The name of the Step */
  def name: String

  /** when to consider the Step thread hung and schedule another one.
    * This does not cancel the execution.
    */
  def timeoutSeconds: Int

  /** How long to wait before the next attempt when the step returns InProgress.
    * If you make a change to this function for a step, you should bump versionOverride
    * to the next integer
    */
  def inProgressSleepSecondsFn: InProgressSleepFunction

  /** If you make a change to the inProgressSleepSecondsFn, you'll have to bump this number, or
    * SSWF won't detect and register the new version of the workflow step.
    * You will NOT have to update this number for the timeoutSeconds, since it's automatically
    * tracked.
    */
  def versionOverride: Int = 0
}

trait InProgressSleepFunction {
  def apply(invocationNum: Int, cumulativeStepDurationSeconds: Int): Int
}

class ConstantInProgressSleepFunction(secondsToWait: Int) extends InProgressSleepFunction {
  override def apply(invocationNum: Int, cumulativeStepDurationSeconds: Int): Int = secondsToWait
}
