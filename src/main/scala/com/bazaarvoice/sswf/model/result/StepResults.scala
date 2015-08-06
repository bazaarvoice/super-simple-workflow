package com.bazaarvoice.sswf.model.result

/**
 * Messages to signal state transitions from action steps
 * @param message An optional text description of what happened in the step
 */
sealed abstract class StepResult(message: Option[String]) {
  def isSuccessful: Boolean
  def isInProgress: Boolean
}

case class Success(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  lazy val isSuccessful = true
  override def isInProgress: Boolean = false
}

object Success {
  def apply() = new Success()
  def apply(msg: String) = new Success(msg)
  def apply(msg: Any) = new Success(msg.toString)
}

case class InProgress(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  lazy val isSuccessful = false
  override def isInProgress: Boolean = true
}

object InProgress {
  def apply() = new InProgress()
  def apply(msg: String) = new InProgress(msg)
  def apply(msg: Any) = new InProgress(msg.toString)
}

case class Failed(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  lazy val isSuccessful = false
  override def isInProgress: Boolean = false
}

object Failed {
  def apply() = new Failed()
  def apply(msg: String) = new Failed(msg)
  def apply(msg: Any) = new Failed(msg.toString)
}

case class TimedOut(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  lazy val isSuccessful = false
  override def isInProgress: Boolean = false
}

object TimedOut {
  def apply() = new TimedOut()
  def apply(msg: String) = new TimedOut(msg)
  def apply(msg: Any) = new TimedOut(msg.toString)
}

object StepResult {
  def deserialize(string: String): StepResult =
    string.split(":").toList match {
      case "SUCCESS" :: Nil => Success(None)
      case "SUCCESS" :: msg => Success(Some(msg.mkString(":")))
      case "IN_PROGRESS" :: Nil => InProgress(None)
      case "IN_PROGRESS" :: msg => InProgress(Some(msg.mkString(":")))
      case "FAILED" :: Nil => Failed(None)
      case "FAILED" :: msg => Failed(Some(msg.mkString(":")))
      case "TIMED_OUT" :: Nil => TimedOut(None)
      case "TIMED_OUT" :: msg => TimedOut(Some(msg.mkString(":")))
      case _ => throw new IllegalArgumentException(string)
    }

  def serialize(message: StepResult) = message match {
    case Success(Some(msg)) => "SUCCESS:" + msg
    case Success(None) => "SUCCESS"
    case InProgress(Some(msg)) => "IN_PROGRESS:" + msg
    case InProgress(None) => "IN_PROGRESS"
    case Failed(Some(msg)) => "FAILED:" + msg
    case Failed(None) => "FAILED"
    case TimedOut(Some(msg)) => "TIMED_OUT:"+msg
    case TimedOut(None) => "TIMED_OUT"
  }
}



