package com.bazaarvoice.sswf

/**
 * Marshaller for workflow input. Simply provide the mechanism to serialize and deserialize workflow inputs.
 * @tparam SSWFInput The JVM object representing your workflow input.
 */
trait InputParser[SSWFInput] {
  def serialize(input: SSWFInput): String
  def deserialize(inputString: String): SSWFInput
}
