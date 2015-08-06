package com.bazaarvoice.sswf.model.history

/**
 * For history events that pertain to the workflow itself, rather than individual steps
 */
object WorkflowEventToken {
  override def toString = "WORKFLOW"
}
