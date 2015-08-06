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
  def startToFinishTimeoutSeconds: Int

  /** How long to wait before the next attempt when the step returns InProgress */
  def inProgressTimerSeconds: Int
}
