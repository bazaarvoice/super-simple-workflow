package com.bazaarvoice.sswf.model.result

import scala.collection.JavaConversions._

/**
  * Messages to signal state transitions from action steps
  *
  * @param message An optional text description of what happened in the step
  */
sealed abstract class StepResult(message: Option[String]) {
  require(!message.exists(_.contains(":")), "Message may not contain the ':' character.")

  def isSuccessful: Boolean
  def isInProgress: Boolean
  def isFinal: Boolean
}

case class Success(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  lazy val isSuccessful = true
  override def isInProgress: Boolean = false
  override def isFinal: Boolean = true
}

case class InProgress(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  lazy val isSuccessful = false
  override def isInProgress: Boolean = true
  override def isFinal: Boolean = false
}

case class Wait(message: Option[String], waitSeconds: Int, signal: String, signals: List[String]) extends StepResult(message) {
  require(!signal.contains(":"), s"Signals may not contain the ':' character [$signal].")
  require(!signals.exists(_.contains(":")), s"Signals may not contain the ':' character [${signals.mkString(",")}].")
  require(waitSeconds>0, s"waitSeconds must be > 0 [$waitSeconds]")

  def this(waitSeconds: Int, signal0: String) = this(None, waitSeconds, signal0, Nil)
  def this(waitSeconds: Int, signal0: String, signal1: String) = this(None, waitSeconds, signal0, List(signal1))
  def this(waitSeconds: Int, signal0: String, signal1: String, signal2: String) = this(None, waitSeconds, signal0, List(signal1, signal2))
  def this(waitSeconds: Int, signal0: String, signal1: String, signal2: String, signal3: String) = this(None, waitSeconds, signal0, List(signal1, signal2, signal3))
  def this(waitSeconds: Int, signal0: String, signal1: String, signal2: String, signal3: String, signal4: String) = this(None, waitSeconds, signal0, List(signal1, signal2, signal3, signal4))
  def this(waitSeconds: Int, signal0: String, signal1: String, signal2: String, signal3: String, signal4: String, signal5: String) =
    this(None, waitSeconds, signal0, List(signal1, signal2, signal3, signal4, signal5))
  def this(waitSeconds: Int, signal0: String, signals: java.util.List[String]) = this(None, waitSeconds, signal0, signals.toList)
  def this(message: String, waitSeconds: Int, signal0: String, signals: java.util.List[String]) = this(Some(message), waitSeconds, signal0, signals.toList)

  lazy val isSuccessful = false
  override def isInProgress: Boolean = true
  override def isFinal: Boolean = true
}

case class Failed(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  lazy val isSuccessful = false
  override def isInProgress: Boolean = false
  override def isFinal: Boolean = true
}

case class Cancelled(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  lazy val isSuccessful = false
  override def isInProgress: Boolean = false
  override def isFinal: Boolean = true
}

case class TimedOut(timeoutType: String, resumeInfo: Option[String]) extends StepResult(Some(s"$timeoutType-$resumeInfo")) {
  lazy val isSuccessful = false
  override def isInProgress: Boolean = false
  override def isFinal: Boolean = false
}

object StepResult {
  def deserialize(string: String): StepResult =
    string.split(":").toList match {
      case "SUCCESS" :: Nil => Success(None)
      case "SUCCESS" :: msg :: Nil => Success(Some(msg))
      case "IN_PROGRESS" :: Nil => InProgress(None)
      case "IN_PROGRESS" :: msg :: Nil => InProgress(Some(msg))
      case "WAIT" :: "" :: waitSeconds :: signal0 :: signals => Wait(None, waitSeconds.toInt, signal0, signals)
      case "WAIT" :: msg :: waitSeconds :: signal0 :: signals => Wait(Some(msg), waitSeconds.toInt, signal0, signals)
      case "FAILED" :: Nil => Failed(None)
      case "FAILED" :: msg :: Nil => Failed(Some(msg))
      case "CANCELLED" :: Nil => Cancelled(None)
      case "CANCELLED" :: msg :: Nil => Cancelled(Some(msg))
      case "TIMED_OUT" :: timeoutType :: resumeInfo => TimedOut(timeoutType, if (resumeInfo.forall(_.isEmpty)) None else Some(resumeInfo.mkString(":")))
      case _ => throw new IllegalArgumentException(string)
    }

  def serialize(message: StepResult) = message match {
    case Success(Some(msg)) => "SUCCESS:" + msg
    case Success(None) => "SUCCESS"
    case InProgress(Some(msg)) => "IN_PROGRESS:" + msg
    case InProgress(None) => "IN_PROGRESS"
    case Wait(msg, waitSeconds, signal0, signals) => s"WAIT:${msg.getOrElse("")}:$waitSeconds:$signal0:${signals.mkString(":")}"
    case Failed(Some(msg)) => "FAILED:" + msg
    case Failed(None) => "FAILED"
    case Cancelled(Some(msg)) => "CANCELLED:" + msg
    case Cancelled(None) => "CANCELLED"
    case TimedOut(timeoutType, resumeInfo) => s"TIMED_OUT:$timeoutType:${resumeInfo.getOrElse("")}"
  }
}



