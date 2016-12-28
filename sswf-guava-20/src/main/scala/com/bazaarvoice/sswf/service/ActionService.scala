package com.bazaarvoice.sswf.service

import java.lang.Thread.UncaughtExceptionHandler
import java.util.concurrent._

import com.amazonaws.AbortedException
import com.amazonaws.services.simpleworkflow.model.{ActivityTask, RespondActivityTaskCompletedRequest}
import com.bazaarvoice.sswf.WorkflowStep
import com.google.common.util.concurrent.AbstractScheduledService.Scheduler
import com.google.common.util.concurrent.{AbstractScheduledService, ThreadFactoryBuilder}
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag

class ActionService[SSWFInput, StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag](stepActionWorker: StepActionWorker[SSWFInput, StepEnum],
                                                                                          workPoolMaxSize: Int)
   extends AbstractScheduledService {
  private val LOG = LoggerFactory.getLogger(getClass)

  private val workerPool = new ThreadPoolExecutor(
    1, workPoolMaxSize, // core and max pool size
    20, TimeUnit.SECONDS, // keep-alive time is long enough to span several polls
    new LinkedBlockingQueue[Runnable](workPoolMaxSize * 2), // allow us to buffer up enough work to keep the workers busy
    new ThreadFactoryBuilder()
       .setNameFormat("action-worker-%d")
       .setUncaughtExceptionHandler(new UncaughtExceptionHandler {
         override def uncaughtException(thread: Thread, throwable: Throwable): Unit =
           LOG.error(s"Exception in action worker [$thread]", throwable)
       })
       .build()
  )

  override def scheduler(): Scheduler = Scheduler.newFixedDelaySchedule(15, 5, TimeUnit.SECONDS)

  private case class PollException(cause: Throwable) extends Throwable

  private def poll(): ActivityTask =
    try {
      stepActionWorker.pollForWork()
    } catch {
      case t: Throwable => throw PollException(t)
    }

  override def runOneIteration(): Unit =
    try {
      LOG.info("Polling for work")

      // Don't just poll once; instead, drain the work queue by handling all pending decisions
      var task: ActivityTask = poll()
      while (task != null && isRunning) {
        LOG.info(s"Got task for {}", task.getWorkflowExecution)
        workerPool.execute(new Runnable {
          override def run(): Unit = {
            val completedRequest: RespondActivityTaskCompletedRequest = stepActionWorker.doWork(task)
            LOG.info(s"Completed action for ${task.getWorkflowExecution}: ${if (completedRequest == null) "null" else completedRequest.getResult}")
          }
        })
        task = poll()
      }

      LOG.info("Done polling for work.")
    } catch {
      case e: AbortedException           =>
        LOG.info("Action thread shutting down...")
      case PollException(t)              =>
        LOG.error("Unexpected exception polling for task. Continuing...", t)
      case e: RejectedExecutionException =>
        LOG.info("Work queue is full. Sleeping...")
    }
  override def shutDown(): Unit = {
    workerPool.shutdown()
    super.shutDown()
  }
}
