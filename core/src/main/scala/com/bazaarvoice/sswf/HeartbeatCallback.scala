package com.bazaarvoice.sswf

trait HeartbeatCallback {
  /**
   * Report liveness and progress. Response `true` if cancellation is requested.
   * @param progressMessage Report any information about your progress.
   * @return `true` if cancellation is requested.
   */
  def checkIn(progressMessage: String): Boolean
}
