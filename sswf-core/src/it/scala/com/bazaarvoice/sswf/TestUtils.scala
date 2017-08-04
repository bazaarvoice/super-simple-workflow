package com.bazaarvoice.sswf

object TestUtils {
  def untilNotNull[R](action: => R): R = {
    while (true) {
      val r: R = action
      if (r != null) {
        return r
      }
    }
    throw new Exception()
  }
}
