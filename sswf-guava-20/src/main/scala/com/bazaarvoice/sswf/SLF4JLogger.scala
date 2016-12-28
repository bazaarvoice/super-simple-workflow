package com.bazaarvoice.sswf

import org.slf4j.LoggerFactory

class SLF4JLogger(logger: org.slf4j.Logger) extends Logger{
  def this(clazz: Class[_]) = this(LoggerFactory.getLogger(clazz))

  override def trace(message: => String): Unit = logger.trace(message)
  override def debug(message: => String): Unit = logger.debug(message)
  override def info(message: => String): Unit = logger.info(message)
  override def warn(message: => String): Unit = logger.warn(message)
  override def warn(message: => String, throwable: Throwable): Unit = logger.warn(message, throwable)
  override def error(message: => String): Unit = logger.error(message)
  override def error(message: => String, throwable: Throwable): Unit = logger.error(message, throwable)
}
