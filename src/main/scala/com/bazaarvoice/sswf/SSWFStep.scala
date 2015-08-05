package com.bazaarvoice.sswf

/**
 * Your Step Enum must implement/extend this
 */
trait SSWFStep {
  /** The name of the Step */
  def name: String
  /** The version of the step definition (increment if you change any options) */
  def version:String

  /** when to consider the Step thread hung and schedule another one.
    * This does not cancel the execution.
    */
  def startToFinishTimeout: Int

  /** How long to wait before the next attempt when the step returns InProgress */
  def inProgressTimerSeconds: Int
}
