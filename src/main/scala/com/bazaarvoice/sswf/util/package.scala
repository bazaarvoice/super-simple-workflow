package com.bazaarvoice.sswf

import com.bazaarvoice.sswf.model.StepInput

package object util {
  // In packing/unpacking the input, we jump through a couple of hoops so we don't have to forbid Some("") in the stepInput.
  // The only restriction is that they can't pass a "\0" (enforced in the ScheduledStep constructor
  private[sswf] def packInput[SSWFInput](inputParser: InputParser[SSWFInput])(stepInput: StepInput, wfInput: SSWFInput): String =
    stepInput.stepInputString.getOrElse("\u0000") + "\u0000" + stepInput.resumeProgress.getOrElse("") + "\u0000" + inputParser.serialize(wfInput)

  private[sswf] def unpackInput[SSWFInput](inputParser: InputParser[SSWFInput])(packedInput: String): (StepInput, SSWFInput) = {
    if (packedInput.startsWith("\u0000\u0000")) {
      val stepInput = None
      val (resume :: input) = packedInput.substring(2).split('\u0000').toList
      val resumeInfo = if (resume.isEmpty) None else Some(resume)
      val wfInput = inputParser.deserialize(input.mkString("\u0000"))
      (StepInput(stepInput, resumeInfo), wfInput)
    } else {
      val (stepIn :: resume :: input) = packedInput.split('\u0000').toList
      val stepInput = if (stepIn.isEmpty) None else Some(stepIn)
      val resumeInfo = if (resume.isEmpty) None else Some(resume)
      val wfInput = inputParser.deserialize(input.mkString("\u0000"))
      (StepInput(stepInput, resumeInfo), wfInput)
    }
  }

  private[sswf] def packTimer(stepName: String, stepInput: StepInput) = {
    stepName + "\u0000" + stepInput.stepInputString.getOrElse("\u0000") + "\u0000" + stepInput.resumeProgress.getOrElse("")
  }

  private[sswf] def unpackTimer(control: String) = {
    val firstDelim: Int = control.indexOf("\u0000")
    val stepName = control.substring(0,firstDelim)
    val packedStepInput = control.substring(firstDelim+1)
    if (packedStepInput.startsWith("\u0000\u0000")) {
      val stepInput = None
      val resume = packedStepInput.substring(2)
      val resumeInfo = if (resume.isEmpty) None else Some(resume)
      (stepName, StepInput(stepInput, resumeInfo))
    } else {
      val (stepIn :: resume) = packedStepInput.split('\u0000').toList
      val stepInput = if (stepIn.isEmpty) None else Some(stepIn)
      val resume2 = resume.mkString("\u0000")
      val resumeInfo = if (resume2.isEmpty) None else Some(resume2)
      (stepName, StepInput(stepInput, resumeInfo))
    }
  }
}
