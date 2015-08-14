package com.bazaarvoice.sswf

package object util {
  // In packing/unpacking the input, we jump through a couple of hoops so we don't have to forbid Some("") in the stepInput.
  // The only restriction is that they can't pass a "\0" (enforced in the ScheduledStep constructor
  private[sswf] def packInput[SSWFInput](inputParser: InputParser[SSWFInput])(stepInput: Option[String], wfInput: SSWFInput): String =
    stepInput.getOrElse("\u0000") + "\u0000" + inputParser.serialize(wfInput)

  private[sswf] def unpackInput[SSWFInput](inputParser: InputParser[SSWFInput])(packedInput: String): (Option[String], SSWFInput) = {
    val (stepInput, serializedWfInput) =
      if (packedInput.startsWith("\u0000\u0000")) {
        (None, packedInput.substring(2))
      } else {
        val stepInput :: wfInput = packedInput.split("\u0000").toList
        (Some(stepInput), wfInput.mkString("\u0000"))
      }

    (stepInput, inputParser.deserialize(serializedWfInput))
  }
}
