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

  /** How long to wait before the next attempt when the step returns InProgress. */
  def inProgressSleepSecondsFn: InProgressSleepFunction

  /** You shouldn't have to override this, but in case you make some change to a step that
    * SSWF can't detect, you can bump this number up to tell SSWF to register a new version of the
    * step.
    * @return a single integer that forces a version update for step registration.
    */
  def versionOverride: Int = 0
}

trait InProgressSleepFunction {
  def apply(invocationNum: Int, cumulativeStepDurationSeconds: Int): Int
}

class ConstantInProgressSleepFunction(secondsToWait: Int) extends InProgressSleepFunction {
  override def apply(invocationNum: Int, cumulativeStepDurationSeconds: Int): Int = secondsToWait
}
