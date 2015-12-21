package com.bazaarvoice.sswf

trait Logger {
  def trace(message: => String): Unit
  def debug(message: => String): Unit
  def info(message: => String): Unit
  def warn(message: => String): Unit
  def warn(message: => String, throwable: Throwable): Unit
  def error(message: => String): Unit
  def error(message: => String, throwable: Throwable): Unit
}
