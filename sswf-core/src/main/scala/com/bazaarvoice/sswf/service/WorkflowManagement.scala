package com.bazaarvoice.sswf.service

import java.util.{Collections, Date}

import com.amazonaws.services.simpleworkflow.model._
import com.amazonaws.services.simpleworkflow.{AmazonSimpleWorkflow, model}
import com.bazaarvoice.sswf.model.history.{HistoryFactory, StepsHistory}
import com.bazaarvoice.sswf.service.except.WorkflowManagementException
import com.bazaarvoice.sswf.{InputParser, Logger, WorkflowStep}

import scala.collection.JavaConverters._
import scala.reflect._
import scala.util.Random


/**
  * This is where you register and start workflows.
  * <br/>
  * In SWF, there are domains, workflow types, activities that need to be registered. You provide all the config we need for activities in the StepEnum. You give the parameters for domain and
  * workflow type here.
  * <br/>
  * This class is implemented so that you can call registerWorkflow() every time your app starts, and we will register anything that needs to be registered, so you should be able to manage your
  * whole workflow from this library.
  *
  * @param domain                               The domain of the workflow: <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-domain.html">AWS docs</a>
  *                                             as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
  * @param taskList                             If you execute the same workflow in different environments, use different task lists. Think of them as independent sets of actors working on the same
  *                                             logical workflow, but in
  *                                             different contexts (like production/qa/development/your machine).
  *                                             <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-task-lists.html">AWS docs</a>
  *                                             as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things"> the README</a>
  * @param workflow                             The id of your particular workflow. See "WorkflowType" in <a href="http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-obj-ident
  *                                             .html">the AWS docs</a>
  *                                             as well as <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things">the README</a>.
  * @param workflowVersion                      You can version workflows, although it's not clear what purpose that serves. Advice: just think of this as an administrative notation. See
  *                                             <a href="https://github.com/bazaarvoice/super-simple-workflow/blob/master/README.md#managing-versions-and-names-of-things">the README</a>.
  * @param swf                                  The AWS SWF client
  * @param workflowExecutionTimeoutSeconds      How long to let the entire workflow run. This only comes in to play if 1) The decision threads die or 2) A step gets into an "infinite loop" in which it
  *                                             always returns InProgress without making any actual progress. The default is set to one month on the assumption that you'll monitor the workflow and
  *                                             fix either of those problems if they occur, letting the workflow resume and complete. If you prefer to let the workflow fail, you'll want to set it
  *                                             lower.
  * @param workflowExecutionRetentionPeriodDays How long to keep _completed_ workflow information. Default: one month.
  * @param stepScheduleToStartTimeoutSeconds    The duration you expect to pass _after_ a task is scheduled, and _before_ an actionWorker picks it up. If there is always a free actionWorker, this is
  *                                             just the polling interval for actions to execute. If all the actionWorkers are busy, though, the action may time out waiting to start. This isn't
  *                                             harmful, though, since the decisionWorker will simply re-schedule it. Advice: make your actionWorker pool large enough that all scheduled work can
  *                                             execute immediately, and set this timeout to the polling interval for action work. Default: 5 minutes
  * @param inputParser                          See InputParser
  * @tparam StepEnum The enum containing workflow step definitions
  */
class WorkflowManagement[SSWFInput, StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag](domain: String,
                                                                                               workflow: String,
                                                                                               workflowVersion: String,
                                                                                               taskList: String,
                                                                                               swf: AmazonSimpleWorkflow,
                                                                                               workflowExecutionTimeoutSeconds: Int = 60 * 60 * 24 * 30, // default: one month
                                                                                               workflowExecutionRetentionPeriodDays: Int = 30,
                                                                                               stepScheduleToStartTimeoutSeconds: Int = 60 * 5,
                                                                                               inputParser: InputParser[SSWFInput],
                                                                                               log: Logger) {


  /**
    * Register the domain,workflow,and activities if they are not already registered.
    * If you have marked any of these as DEPRECATED through the SWF api, this method will throw an exception telling you to delete the relevant config first.
    */
  def registerWorkflow(): Unit = {
    registerDomain()
    registerWorkflowType()
    registerActivities()
  }


  /**
    * Submit/start a workflow execution.
    *
    * @param workflowId A unique identifier for this particular workflow execution
    * @param input      Whatever input you need to provide to the workflow
    * @return tracking information for the workflow
    */
  def startWorkflow(workflowId: String, input: SSWFInput): WorkflowExecution = {
    val inputString = inputParser.serialize(input)
    val run =
      try {
        swf.startWorkflowExecution(new StartWorkflowExecutionRequest()
           .withDomain(domain)
           .withTaskList(new TaskList().withName(taskList))
           .withWorkflowId(workflowId)
           .withWorkflowType(new WorkflowType().withName(workflow).withVersion(workflowVersion))
           .withInput(inputString)
        )
      } catch {
        case t: Throwable =>
          throw new WorkflowManagementException(s"Exception starting workflow[$workflowId]", t)
      }

    new WorkflowExecution().withRunId(run.getRunId).withWorkflowId(workflowId)
  }

  def terminateWorkflowExecution(workflowId: String, runId: String): Unit = {
    try {
      swf.terminateWorkflowExecution(new TerminateWorkflowExecutionRequest()
         .withDomain(domain)
         .withWorkflowId(workflowId)
         .withRunId(runId)
      )
    } catch {
      case t: Throwable =>
        throw new WorkflowManagementException(s"Exception terminating workflow[$workflowId] run[$runId]", t)
    }
  }

  def cancelWorkflowExecution(workflowId: String, runId: String): Unit = {
    try {
      swf.requestCancelWorkflowExecution(new RequestCancelWorkflowExecutionRequest()
         .withDomain(domain)
         .withWorkflowId(workflowId)
         .withRunId(runId)
      )
    } catch {
      case t: Throwable =>
        throw new WorkflowManagementException(s"Exception cancelling workflow[$workflowId] run[$runId]", t)
    }
  }

  /**
    * Use this method to generate signals for use with the Wait result
    *
    * The format of the signal is publicly documented as 3 pipe-delimited fields with workflowId,runId, and a unique signal name.
    *
    * WARNING: this format is public, so if you change it, you need to update the docs.
    *
    * @return a signal token for passing to Wait and for calling signalWorkflow with.
    */
  def generateSignal(workflowId: String, runId: String) = workflowId + "|" + runId + "|" + new Random().nextLong().toHexString

  /**
    * Use this method to send a previously generated signal to the workflow that generated it.
    */
  def signalWorkflow(signal: String) = {
    signal.split('|').toList match {
      case workflowId :: runId :: _ :: Nil =>
        try {
          swf.signalWorkflowExecution(new SignalWorkflowExecutionRequest()
             .withDomain(domain)
             .withWorkflowId(workflowId)
             .withRunId(runId)
             .withSignalName(signal)
          )
        } catch {
          case t: Throwable =>
            throw new WorkflowManagementException(s"Exception signalling domain[$domain] workflow[$workflowId] run[$runId] signal[$signal]", t)
        }
      case _                               =>
        throw new IllegalArgumentException(s"Incorrectly formatted signal [$signal]")
    }
  }

  /**
    * List open executions for this domain and workflow within the time window. (regardless of version)
    *
    * @param from Start time to search
    * @param to   End time to search
    * @return an unmodifiable list of matching executions
    */
  def listOpenExecutions(from: Date, to: Date): java.util.List[WorkflowExecutionInfo] = {
    val openRequest: ListOpenWorkflowExecutionsRequest = new ListOpenWorkflowExecutionsRequest()
       .withDomain(domain)
       .withTypeFilter(new WorkflowTypeFilter().withName(workflow))
       .withStartTimeFilter(new ExecutionTimeFilter().withLatestDate(to).withOldestDate(from))

    innerListOpenExecutions(openRequest)
  }

  /**
    * List open executions for this domain, workflow, and workflowId within the time window. (regardless of version)
    *
    * @param from       Start time to search
    * @param to         End time to search
    * @param workflowId The particular workflow id to list executions for
    * @return an unmodifiable list of matching executions
    */
  def listOpenExecutions(from: Date, to: Date, workflowId: String): java.util.List[WorkflowExecutionInfo] = {
    val openRequest: ListOpenWorkflowExecutionsRequest = new ListOpenWorkflowExecutionsRequest()
       .withDomain(domain)
       .withExecutionFilter(new WorkflowExecutionFilter().withWorkflowId(workflowId))
       .withStartTimeFilter(new ExecutionTimeFilter().withLatestDate(to).withOldestDate(from))

    innerListOpenExecutions(openRequest)
  }


  /**
    * List closed executions for this domain and workflow within the time window. (regardless of version)
    *
    * @param from Start time to search
    * @param to   End time to search
    * @return an unmodifiable list of matching executions
    */
  def listClosedExecutions(from: Date, to: Date): java.util.List[WorkflowExecutionInfo] = {

    val closedRequest: ListClosedWorkflowExecutionsRequest = new ListClosedWorkflowExecutionsRequest()
       .withDomain(domain)
       .withTypeFilter(new WorkflowTypeFilter().withName(workflow))
       .withStartTimeFilter(new ExecutionTimeFilter().withLatestDate(to).withOldestDate(from))

    innerListClosedExecutions(closedRequest)
  }

  /**
    * List closed executions for this domain, workflow, and workflowId within the time window. (regardless of version)
    *
    * @param from       Start time to search
    * @param to         End time to search
    * @param workflowId The particular workflow id to list executions for
    * @return an unmodifiable list of matching executions
    */
  def listClosedExecutions(from: Date, to: Date, workflowId: String): java.util.List[WorkflowExecutionInfo] = {

    val closedRequest: ListClosedWorkflowExecutionsRequest = new ListClosedWorkflowExecutionsRequest()
       .withDomain(domain)
       .withExecutionFilter(new WorkflowExecutionFilter().withWorkflowId(workflowId))
       .withStartTimeFilter(new ExecutionTimeFilter().withLatestDate(to).withOldestDate(from))

    innerListClosedExecutions(closedRequest)
  }

  /**
    * List all the events from an execution of a workflow.
    *
    * @param workflowId The particular workflow id to list events for
    * @param runId      The particular run of the workflow id to list events for
    * @return
    */
  def describeExecution(workflowId: String, runId: String): StepsHistory[SSWFInput, StepEnum] = {
    val request: GetWorkflowExecutionHistoryRequest = new GetWorkflowExecutionHistoryRequest()
       .withDomain(domain)
       .withExecution(new model.WorkflowExecution().withWorkflowId(workflowId).withRunId(runId))

    val iterateFn = (prev: History) => {
      if (prev == null || prev.getNextPageToken == null) null
      else swf.getWorkflowExecutionHistory(request.withNextPageToken(prev.getNextPageToken))
    }

    val historyEvents =
      Stream
         .iterate(swf.getWorkflowExecutionHistory(request))(iterateFn)
         .takeWhile(_ != null)
         .flatten(_.getEvents.asScala)
         .toList

    HistoryFactory.from(historyEvents, inputParser)
  }


  private[this] def registerDomain(): Unit = {
    // Opt for try/catch rather than list(), since the missing exception will probably be thrown once for each domain. List, on the other hand, would be expensive every time.
    try {
      val domainDetail: DomainDetail = swf.describeDomain(new DescribeDomainRequest().withName(domain))
      assert(domainDetail.getDomainInfo.getStatus == "REGISTERED", s"domain[$domain] is not REGISTERED [${domainDetail.getDomainInfo.getStatus}]. Please delete it and then re-run.")
    } catch {
      case e: UnknownResourceException =>
        try {
          swf.registerDomain(new RegisterDomainRequest()
             .withName(domain)
             .withDescription(s"domain[$domain] created by SSWF at [${new Date()}]")
             .withWorkflowExecutionRetentionPeriodInDays(workflowExecutionRetentionPeriodDays.toString))
        } catch {
          case d: DomainAlreadyExistsException =>
            // race condition. ignore...
            ()
          case t: Throwable                    =>
            throw new WorkflowManagementException(s"Unexpected exception registering domain[$domain]", t)
        }
    }
  }

  private[this] def registerWorkflowType(): Unit = {
    try {
      val workflowType: WorkflowTypeDetail = swf.describeWorkflowType(new DescribeWorkflowTypeRequest().withDomain(domain).withWorkflowType(new WorkflowType().withName(workflow).withVersion
      (workflowVersion)))
      assert(workflowType.getTypeInfo.getStatus == "REGISTERED", s"workflow[$workflow/$workflowVersion] is not REGISTERED [${workflowType.getTypeInfo.getStatus}]. Please delete it and then re-run.")
    } catch {
      case e: UnknownResourceException =>
        try {
          swf.registerWorkflowType(new RegisterWorkflowTypeRequest()
             .withDomain(domain)
             .withName(workflow)
             .withVersion(workflowVersion)
             .withDescription(s"workflow[$workflow/$workflowVersion] registered by SSWF at [${new Date()}}]")
             .withDefaultExecutionStartToCloseTimeout(workflowExecutionTimeoutSeconds.toString)
             .withDefaultTaskStartToCloseTimeout(600.toString) // timeout for decision tasks
             .withDefaultChildPolicy(ChildPolicy.TERMINATE)
          )
        } catch {
          case e: TypeAlreadyExistsException =>
            // race condition. ignore...
            ()
          case t: Throwable                  =>
            throw new WorkflowManagementException(s"Unexpected exception registering domain[$domain] workflow[$workflow/$workflowVersion]", t)
        }
    }
  }

  private[this] def streamActivityTypes(listActivityTypesRequest: ListActivityTypesRequest): Set[(String, String)] = {
    val iterateFn = (prev: ActivityTypeInfos) =>
      if (prev == null || prev.getNextPageToken == null) null
      else swf.listActivityTypes(listActivityTypesRequest.withNextPageToken(prev.getNextPageToken))

    Stream
       .iterate(swf.listActivityTypes(listActivityTypesRequest))(iterateFn)
       .takeWhile(_ != null)
       .flatten(r => collectionAsScalaIterable(r.getTypeInfos))
       .map(i => (i.getActivityType.getName, i.getActivityType.getVersion))
       .toSet
  }

  private[this] def registerActivities() {
    val baseRequest: ListActivityTypesRequest = new ListActivityTypesRequest()
       .withDomain(domain)
       .withMaximumPageSize(100)

    val registered = streamActivityTypes(baseRequest.withRegistrationStatus(RegistrationStatus.REGISTERED))
    val deprecated = streamActivityTypes(baseRequest.withRegistrationStatus(RegistrationStatus.DEPRECATED))

    val activities = registered union deprecated
    def register(activity: StepEnum) {
      val version = util.stepToVersion(activity)
      if (!activities.contains((activity.name, version))) {
        try {
          swf.registerActivityType(new RegisterActivityTypeRequest()
             .withName(activity.name)
             .withVersion(version)
             .withDomain(domain)
             .withDefaultTaskList(new TaskList().withName(taskList))
             .withDefaultTaskHeartbeatTimeout(activity.timeoutSeconds.toString)
             .withDefaultTaskScheduleToStartTimeout(stepScheduleToStartTimeoutSeconds.toString)
             .withDefaultTaskScheduleToCloseTimeout((stepScheduleToStartTimeoutSeconds + activity.timeoutSeconds).toString)
             .withDefaultTaskStartToCloseTimeout(activity.timeoutSeconds.toString)
          )
        } catch {
          case t: Throwable =>
            throw new WorkflowManagementException(s"Exception registering activity[${activity.name}/$version] domain[$domain]", t)
        }
      }
    }

    val stepEnumClass: Class[StepEnum] = classTag[StepEnum].runtimeClass.asInstanceOf[Class[StepEnum]]
    stepEnumClass.getEnumConstants foreach register
  }

  private[this] def innerListOpenExecutions(listOpenWorkflowExecutionsRequest: ListOpenWorkflowExecutionsRequest): java.util.List[WorkflowExecutionInfo] = {
    val openStream = {
      val iterateFn: (WorkflowExecutionInfos) => WorkflowExecutionInfos = prev =>
        if (prev == null || prev.getNextPageToken == null) null
        else swf.listOpenWorkflowExecutions(listOpenWorkflowExecutionsRequest.withNextPageToken(prev.getNextPageToken))

      Stream
         .iterate(swf.listOpenWorkflowExecutions(listOpenWorkflowExecutionsRequest))(iterateFn)
         .takeWhile(_ != null)
         .flatten(_.getExecutionInfos.asScala)
    }

    Collections.unmodifiableList(scala.collection.JavaConverters.seqAsJavaList(openStream))
  }


  private[this] def innerListClosedExecutions(listClosedWorkflowExecutionsRequest: ListClosedWorkflowExecutionsRequest): java.util.List[WorkflowExecutionInfo] = {

    val closedStream = {
      val iterateFn: (WorkflowExecutionInfos) => WorkflowExecutionInfos = prev =>
        if (prev == null || prev.getNextPageToken == null) null
        else swf.listClosedWorkflowExecutions(listClosedWorkflowExecutionsRequest.withNextPageToken(prev.getNextPageToken))

      Stream
         .iterate(swf.listClosedWorkflowExecutions(listClosedWorkflowExecutionsRequest))(iterateFn)
         .takeWhile(_ != null)
         .flatten(_.getExecutionInfos.asScala)
    }

    Collections.unmodifiableList(scala.collection.JavaConverters.seqAsJavaList(closedStream))
  }

}
