package com.bazaarvoice.sswf.model.result

import com.fasterxml.jackson.databind.node.{ArrayNode, JsonNodeFactory}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}

import scala.collection.JavaConverters._

/**
  * Messages to signal state transitions from action steps
  *
  * @param message An optional text description of what happened in the step
  */
sealed abstract class StepResult(message: Option[String]) {
  def isInProgress: Boolean
}

case class Success(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  override def isInProgress: Boolean = false
}

case class InProgress(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  override def isInProgress: Boolean = true
}

case class Wait(message: Option[String], waitSeconds: Int, signal: String, signals: List[String]) extends StepResult(message) {
  require(waitSeconds > 0, s"waitSeconds must be > 0 [$waitSeconds]")

  def this(waitSeconds: Int, signal0: String) = this(None, waitSeconds, signal0, Nil)
  def this(waitSeconds: Int, signal0: String, signal1: String) = this(None, waitSeconds, signal0, List(signal1))
  def this(waitSeconds: Int, signal0: String, signal1: String, signal2: String) = this(None, waitSeconds, signal0, List(signal1, signal2))
  def this(waitSeconds: Int, signal0: String, signal1: String, signal2: String, signal3: String) = this(None, waitSeconds, signal0, List(signal1, signal2, signal3))
  def this(waitSeconds: Int, signal0: String, signal1: String, signal2: String, signal3: String, signal4: String) = this(None, waitSeconds, signal0, List(signal1, signal2, signal3, signal4))
  def this(waitSeconds: Int, signal0: String, signal1: String, signal2: String, signal3: String, signal4: String, signal5: String) =
    this(None, waitSeconds, signal0, List(signal1, signal2, signal3, signal4, signal5))
  def this(waitSeconds: Int, signal0: String, signals: java.util.List[String]) = this(None, waitSeconds, signal0, signals.asScala.toList)
  def this(message: String, waitSeconds: Int, signal0: String, signals: java.util.List[String]) = this(Some(message), waitSeconds, signal0, signals.asScala.toList)

  override def isInProgress: Boolean = true
}

case class Failed(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  override def isInProgress: Boolean = false
}

case class Cancelled(message: Option[String]) extends StepResult(message) {
  def this() = this(None)
  def this(msg: String) = this(Some(msg))

  override def isInProgress: Boolean = false
}

case class TimedOut(timeoutType: String, resumeInfo: Option[String]) extends StepResult(Some(s"$timeoutType-$resumeInfo")) {
  override def isInProgress: Boolean = true
}

object StepResult {
  val mapper = new ObjectMapper()
  val json = JsonNodeFactory.instance

  def deserialize(string: String): StepResult = {
    val node = mapper.readTree(string)

    node.get("result").asText() match {
      case "SUCCESS"     => Success(Option(node.get("message")).map(_.asText()))
      case "IN_PROGRESS" => InProgress(Option(node.get("message")).map(_.asText()))
      case "WAIT"        =>
        val signal0 :: signals = node.get("signals").elements().asScala.map(_.asText()).toList
        Wait(Option(node.get("message")).map(_.asText()),
          node.get("waitSeconds").asInt(),
          signal0,
          signals
        )
      case "FAILED"      => Failed(Option(node.get("message")).map(_.asText()))
      case "CANCELLED"   => Cancelled(Option(node.get("message")).map(_.asText()))
      case "TIMED_OUT"   =>
        TimedOut(
          node.get("timeoutType").asText(),
          Option(node.get("resumeInfo")).map(_.asText())
        )
      case _             => throw new IllegalArgumentException(string)
    }
  }

  def serialize(message: StepResult) = {
    val node: JsonNode = message match {
      case Success(Some(msg))    => json.objectNode().put("result", "SUCCESS").put("message", msg)
      case Success(None)         => json.objectNode().put("result", "SUCCESS")
      case InProgress(Some(msg)) => json.objectNode().put("result", "IN_PROGRESS").put("message", msg)
      case InProgress(None)      => json.objectNode().put("result", "IN_PROGRESS")

      case Wait(msg, waitSeconds, signal0, signals) =>
        val signalsNode: ArrayNode = json.arrayNode().add(signal0)
        signals.foreach(signalsNode.add)
        val waitNode = json.objectNode()
           .put("result", "WAIT")
           .put("waitSeconds", waitSeconds)
        msg.foreach(s => waitNode.put("message", s))
        waitNode.set("signals", signalsNode)
      case Failed(Some(msg))                        => json.objectNode().put("result", "FAILED").put("message", msg)
      case Failed(None)                             => json.objectNode().put("result", "FAILED")
      case Cancelled(Some(msg))                     => json.objectNode().put("result", "CANCELLED").put("message", msg)
      case Cancelled(None)                          => json.objectNode().put("result", "CANCELLED")
      case TimedOut(timeoutType, resumeInfo)        =>
        val toNode = json.objectNode()
           .put("result", "TIMED_OUT")
           .put("timeoutType", timeoutType)
        resumeInfo.foreach(s => toNode.put("resumeInfo", s))
        toNode
    }

    mapper.writeValueAsString(node)
  }
}



