package com.bazaarvoice.sswf

import com.bazaarvoice.sswf.model.StepInput
import com.fasterxml.jackson.databind.node.{JsonNodeFactory, ObjectNode}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}

package object util {
  private[this] val mapper = new ObjectMapper
  private[this] val json = JsonNodeFactory.instance

  private[this] def packStepInput(stepInput: StepInput): JsonNode = {
    val node: ObjectNode = json.objectNode()
    stepInput.stepInputString.foreach(s => node.put("stepInputString", s))
    stepInput.resumeProgress.foreach(s => node.put("stepResume", s))
    node
  }

  private[this] def unpackStepInput(node: JsonNode): StepInput = {
    StepInput(
      Option(node.get("stepInputString")).map(_.asText()),
      Option(node.get("stepResume")).map(_.asText())
    )
  }

  private[sswf] def packInput[SSWFInput](inputParser: InputParser[SSWFInput])(stepInput: StepInput, wfInput: SSWFInput): String = {
    val node: ObjectNode = json.objectNode()
    node.set("stepInput", packStepInput(stepInput))
    node.put("wfInput", inputParser.serialize(wfInput))
    mapper.writeValueAsString(node)
  }

  private[sswf] def unpackInput[SSWFInput](inputParser: InputParser[SSWFInput])(packedInput: String): (StepInput, SSWFInput) = {
    val node: JsonNode = mapper.readTree(packedInput)
    (unpackStepInput(node.get("stepInput")), inputParser.deserialize(node.get("wfInput").asText()))
  }

  private[sswf] def packTimer(stepName: String, stepInput: StepInput) = {
    val node: ObjectNode = json.objectNode()
    node.set("stepInput", packStepInput(stepInput))
    node.put("stepName", stepName)
    mapper.writeValueAsString(node)
  }

  private[sswf] def unpackTimer(control: String): (String,StepInput) = {
    val node = mapper.readTree(control)
    (node.get("stepName").asText(),unpackStepInput(node.get("stepInput")))
  }
}
