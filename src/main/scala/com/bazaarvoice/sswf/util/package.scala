package com.bazaarvoice.sswf

package object util {
  // In packing/unpacking the input, we jump through a couple of hoops so we don't have to forbid Some("") in the stepInput.
  // The only restriction is that they can't pass a "\0" (enforced in the ScheduledStep constructor
  private[sswf] def packInput[SSWFInput](inputParser: InputParser[SSWFInput])(stepInput: Option[String], wfInput: SSWFInput): String =
    stepInput.getOrElse("\0") + "\0" + inputParser.serialize(wfInput)

  private[sswf] def unpackInput[SSWFInput](inputParser: InputParser[SSWFInput])(packedInput: String): (Option[String], SSWFInput) = {
    val (stepInput, serializedWfInput) =
      if (packedInput.startsWith("\0\0")) {
        (None, packedInput.substring(2))
      } else {
        val stepInput :: wfInput = packedInput.split("\0").toList
        (Some(stepInput), wfInput.mkString("\0"))
      }

    (stepInput, inputParser.deserialize(serializedWfInput))
  }
}
