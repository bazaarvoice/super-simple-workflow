package com.bazaarvoice.sswf.service

import java.util.{Collections, Date}

import com.amazonaws.services.simpleworkflow.model._
import com.amazonaws.services.simpleworkflow.{AmazonSimpleWorkflow, model}
import com.bazaarvoice.sswf.model.{HistoryFactory, StepsHistory}
import com.bazaarvoice.sswf.{InputParser, SSWFStep}

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.reflect._

case class WorkflowExecution(wfId: String, runId: String)

/**
 * This is where you register and start workflows.
 * <br/>
 * In SWF, there are domains, workflow types, activities that need to be registered. You provide all the config we need for activities in the StepEnum. You give the parameters for domain and
 * workflow type here.
 * <br/>
 * This class is implemented so that you can call registerWorkflow() every time your app starts, and we will register anything that needs to be registered, so you should be able to manage your
 * whole workflow from this library.
 *
 * @param domain A logical scope for all your workflows. It mainly helps to reduce noise in all the SWF list() apis. See http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-domain.html
 * @param workflow The id of your particular workflow. See "WorkflowType" in http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dev-obj-ident.html
 * @param workflowVersion You can version workflows, although it's not clear what purpose that serves. Advice: just think of this as an administrative notation.
 * @param taskList If you execute the same workflow in different environments, use different task lists. Think of them as independent sets of actors working on the same logical workflow, but in
 *                 different contexts (like production/qa/development/your machine).
 * @param swf The AWS SWF client
 * @param workflowExecutionRetentionPeriodInDays How long to keep _completed_ workflow information
 * @param stepScheduleToStartTimeoutSeconds The duration you expect to pass _after_ a task is scheduled, and _before_ an actionWorker picks it up. If there is always a free actionWorker, this is
 *                                          just the polling interval for actions to execute. If all the actionWorkers are busy, though, the action may time out waiting to start. This isn't
 *                                          harmful, though, since the decisionWorker will simply re-schedule it. Advice: make your actionWorker pool large enough that all scheduled work can
 *                                          execute immediately, and set this timeout to the polling interval for action work.
 * @tparam StepEnum The enum containing workflow step definitions
 */
class WorkflowManagement[SSWFInput, StepEnum <: (Enum[StepEnum] with SSWFStep) : ClassTag](domain: String,
                                                                                           workflow: String,
                                                                                           workflowVersion: String,
                                                                                           taskList: String,
                                                                                           swf: AmazonSimpleWorkflow,
                                                                                           workflowExecutionTimeoutSeconds: Int = 60 * 60 * 24 * 30, // default: one month
                                                                                           workflowExecutionRetentionPeriodInDays: Int = 30,
                                                                                           stepScheduleToStartTimeoutSeconds: Int = 60,
                                                                                           inputParser: InputParser[SSWFInput]) {


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
   * @param input Whatever input you need to provide to the workflow
   * @return tracking information for the workflow
   */
  def startWorkflow(workflowId: String, input: SSWFInput): WorkflowExecution = {
    val inputString = inputParser.serialize(input)
    val run = swf.startWorkflowExecution(new StartWorkflowExecutionRequest()
       .withDomain(domain)
       .withTaskList(new TaskList().withName(taskList))
       .withWorkflowId(workflowId)
       .withWorkflowType(new WorkflowType().withName(workflow).withVersion(workflowVersion))
       .withInput(inputString)
    )

    WorkflowExecution(workflowId, run.getRunId)
  }


  /**
   * List all the executions for this domain and workflow within the time window. (regardless of version)
   * @param from Start time to search
   * @param to End time to search
   * @return an unmodifiable list of matching executions
   */
  def listExecutions(from: Date, to: Date): java.util.List[WorkflowExecutionInfo] = {
    val openRequest: ListOpenWorkflowExecutionsRequest = new ListOpenWorkflowExecutionsRequest()
       .withDomain(domain)
       .withTypeFilter(new WorkflowTypeFilter().withName(workflow))
       .withStartTimeFilter(new ExecutionTimeFilter().withLatestDate(to).withOldestDate(from))

    val closedRequest: ListClosedWorkflowExecutionsRequest = new ListClosedWorkflowExecutionsRequest()
       .withDomain(domain)
       .withTypeFilter(new WorkflowTypeFilter().withName(workflow))
       .withStartTimeFilter(new ExecutionTimeFilter().withLatestDate(to).withOldestDate(from))

    innerListExecutions(openRequest, closedRequest)
  }

  /**
   * List all the executions for this domain, workflow, and workflowId within the time window. (regardless of version)
   *
   * @param from Start time to search
   * @param to End time to search
   * @param workflowId The particular workflow id to list executions for
   * @return an unmodifiable list of matching executions
   */
  def listExecutions(from: Date, to: Date, workflowId: String): java.util.List[WorkflowExecutionInfo] = {
    val openRequest: ListOpenWorkflowExecutionsRequest = new ListOpenWorkflowExecutionsRequest()
       .withDomain(domain)
       .withTypeFilter(new WorkflowTypeFilter().withName(workflow))
       .withExecutionFilter(new WorkflowExecutionFilter().withWorkflowId(workflowId))
       .withStartTimeFilter(new ExecutionTimeFilter().withLatestDate(to).withOldestDate(from))

    val closedRequest: ListClosedWorkflowExecutionsRequest = new ListClosedWorkflowExecutionsRequest()
       .withDomain(domain)
       .withTypeFilter(new WorkflowTypeFilter().withName(workflow))
       .withExecutionFilter(new WorkflowExecutionFilter().withWorkflowId(workflowId))
       .withStartTimeFilter(new ExecutionTimeFilter().withLatestDate(to).withOldestDate(from))

    innerListExecutions(openRequest, closedRequest)
  }

  /**
   * List all the events from an execution of a workflow.
   * @param workflowId The particular workflow id to list events for
   * @param runId The particular run of the workflow id to list events for
   * @return
   */
  def describeExecution(workflowId: String, runId: String): StepsHistory[SSWFInput, StepEnum] = {
    val request: GetWorkflowExecutionHistoryRequest = new GetWorkflowExecutionHistoryRequest()
       .withDomain(domain)
       .withExecution(new model.WorkflowExecution().withWorkflowId(workflowId).withRunId(runId))

    val iterateFn = (prev: History) => {
      if (prev == null | prev.getNextPageToken == null) null
      else swf.getWorkflowExecutionHistory(request.withNextPageToken(prev.getNextPageToken))
    }

    val historyEvents =
      Stream
         .iterate(swf.getWorkflowExecutionHistory(request))(iterateFn)
         .takeWhile(_ != null)
         .flatten(_.getEvents)
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
             .withWorkflowExecutionRetentionPeriodInDays(workflowExecutionRetentionPeriodInDays.toString))
        } catch {
          case d: DomainAlreadyExistsException =>
            // race condition. ignore...
            ()
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
             .withDefaultTaskStartToCloseTimeout(60.toString) // timeout for decision tasks
             .withDefaultChildPolicy(ChildPolicy.TERMINATE)
          )
        } catch {
          case e: TypeAlreadyExistsException =>
            // race condition. ignore...
            ()
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
      if (!activities.contains((activity.name, activity.version))) {
        swf.registerActivityType(new RegisterActivityTypeRequest()
           .withName(activity.name)
           .withVersion(activity.version)
           .withDomain(domain)
           .withDefaultTaskList(new TaskList().withName(taskList))
           .withDefaultTaskHeartbeatTimeout(activity.startToFinishTimeout.toString)
           .withDefaultTaskScheduleToStartTimeout(stepScheduleToStartTimeoutSeconds.toString)
           .withDefaultTaskScheduleToCloseTimeout((stepScheduleToStartTimeoutSeconds + activity.startToFinishTimeout).toString)
           .withDefaultTaskStartToCloseTimeout(activity.startToFinishTimeout.toString)
        )
      }
    }

    val stepEnumClass: Class[StepEnum] = classTag[StepEnum].runtimeClass.asInstanceOf[Class[StepEnum]]
    stepEnumClass.getEnumConstants foreach register
  }


  private[this] def innerListExecutions(listOpenWorkflowExecutionsRequest: ListOpenWorkflowExecutionsRequest,
                                        listClosedWorkflowExecutionsRequest: ListClosedWorkflowExecutionsRequest): java.util.List[WorkflowExecutionInfo] = {
    val openStream = {
      val iterateFn: (WorkflowExecutionInfos) => WorkflowExecutionInfos = prev =>
        if (prev == null || prev.getNextPageToken == null) null
        else swf.listOpenWorkflowExecutions(listOpenWorkflowExecutionsRequest.withNextPageToken(prev.getNextPageToken))

      Stream
         .iterate(swf.listOpenWorkflowExecutions(listOpenWorkflowExecutionsRequest))(iterateFn)
         .takeWhile(_ != null)
         .flatten(_.getExecutionInfos)
    }
    val closedStream = {
      val iterateFn: (WorkflowExecutionInfos) => WorkflowExecutionInfos = prev =>
        if (prev == null || prev.getNextPageToken == null) null
        else swf.listClosedWorkflowExecutions(listClosedWorkflowExecutionsRequest.withNextPageToken(prev.getNextPageToken))

      Stream
         .iterate(swf.listClosedWorkflowExecutions(listClosedWorkflowExecutionsRequest))(iterateFn)
         .takeWhile(_ != null)
         .flatten(_.getExecutionInfos)
    }

    Collections.unmodifiableList(scala.collection.JavaConversions.seqAsJavaList(openStream ++ closedStream))
  }

}
