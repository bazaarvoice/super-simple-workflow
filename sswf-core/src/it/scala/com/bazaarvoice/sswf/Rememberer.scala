package com.bazaarvoice.sswf

class Rememberer {
  var toRemember: String = _
  def remember(s: String): Unit = {
    toRemember = s
  }
}
