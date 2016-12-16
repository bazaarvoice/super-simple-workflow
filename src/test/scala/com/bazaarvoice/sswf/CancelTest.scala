package com.bazaarvoice.sswf

import java.lang.Long
import java.util.concurrent.ConcurrentHashMap
import java.util.{Date, UUID}

import com.amazonaws.regions.Region
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow
import com.amazonaws.services.simpleworkflow.model._
import com.amazonaws.{AmazonWebServiceRequest, ResponseMetadata}
import com.bazaarvoice.sswf.model.history.StepsHistory
import com.bazaarvoice.sswf.model.result.{InProgress, StepResult}
import com.bazaarvoice.sswf.model.{DefinedStep, ScheduledStep, StepInput}
import com.bazaarvoice.sswf.service.{StepActionWorker, StepDecisionWorker, WorkflowManagement}
import example.StdOutLogger
import org.scalatest.FlatSpec

import scala.collection.JavaConversions._

class CancelTestWorkflowDef(rememberer: Rememberer) extends WorkflowDefinition[String, TestSteps] {
  override def workflow(input: String): _root_.java.util.List[ScheduledStep[TestSteps]] = List(DefinedStep(TestSteps.INPROGRESS_STEP))
  override def onFinish(workflowId: String, runId: String, input: String, history: StepsHistory[String, TestSteps], message: String): Unit = rememberer.remember("finished")
  override def onCancel(workflowId: String, runId: String, input: String, history: StepsHistory[String, TestSteps], message: String): Unit = rememberer.remember("cancelled")
  override def onFail(workflowId: String, runId: String, input: String, history: StepsHistory[String, TestSteps], message: String): Unit = rememberer.remember("failed")
  override def act(step: TestSteps, input: String, stepInput: StepInput, heartbeatCallback: HeartbeatCallback, execution: WorkflowExecution): StepResult =
    step match {
      case TestSteps.INPROGRESS_STEP => InProgress(None)
    }
}

class Rememberer {
  var toRemember: String = null
  def remember(s: String): Unit = {
    toRemember = s
  }
}

class TestWorkflowEngine extends AmazonSimpleWorkflow {

  case class WFState(startWorkflowExecutionRequest: StartWorkflowExecutionRequest, events: List[HistoryEvent] = Nil)

  val runsForDomain = new ConcurrentHashMap[String, List[String]]()

  val runs = new ConcurrentHashMap[String, WFState]()

  val types = new ConcurrentHashMap[String, List[ActivityTypeInfo]]()

  override def describeDomain(describeDomainRequest: DescribeDomainRequest): DomainDetail = {
    val detail: DomainDetail = new DomainDetail
    val info: DomainInfo = new DomainInfo()
    info.setStatus("REGISTERED")
    detail.setDomainInfo(info)

    detail
  }


  override def describeWorkflowType(describeWorkflowTypeRequest: DescribeWorkflowTypeRequest): WorkflowTypeDetail = {
    val detail: WorkflowTypeDetail = new WorkflowTypeDetail
    val info: WorkflowTypeInfo = new WorkflowTypeInfo
    info.setStatus("REGISTERED")
    detail.setTypeInfo(info)
    detail
  }

  override def listActivityTypes(listActivityTypesRequest: ListActivityTypesRequest): ActivityTypeInfos = {
    val domain: String = listActivityTypesRequest.getDomain

    val infos: ActivityTypeInfos = new ActivityTypeInfos
    infos.setTypeInfos(types.getOrDefault(domain, Nil))
    infos.setNextPageToken(null)
    infos
  }

  override def registerActivityType(registerActivityTypeRequest: RegisterActivityTypeRequest): Unit = {
    val domain: String = registerActivityTypeRequest.getDomain
    val info: ActivityTypeInfo = new ActivityTypeInfo
    val activityType: ActivityType = new ActivityType
    activityType.setName(registerActivityTypeRequest.getName)
    activityType.setVersion(registerActivityTypeRequest.getVersion)
    info.setActivityType(activityType)
    info.setCreationDate(new Date)
    info.setStatus(RegistrationStatus.REGISTERED)
    types.synchronized {
      types.put(domain, info :: types.getOrDefault(domain, Nil))
    }
  }


  override def startWorkflowExecution(startWorkflowExecutionRequest: StartWorkflowExecutionRequest): Run = {
    /*
    val domain: String = startWorkflowExecutionRequest.getDomain
    val taskList: String = startWorkflowExecutionRequest.getTaskList.getName
    val workflowId: String = startWorkflowExecutionRequest.getWorkflowId
    val workflowTypeName: String = startWorkflowExecutionRequest.getWorkflowType.getName
    val workflowTypeVersion: String = startWorkflowExecutionRequest.getWorkflowType.getVersion
    val input: String = startWorkflowExecutionRequest.getInput
    */

    val run: Run = new Run()
    val runId: String = UUID.randomUUID().toString
    run.setRunId(runId)

    runs.synchronized {
      val workflowStart: HistoryEvent = new HistoryEvent
      workflowStart.setWorkflowExecutionStartedEventAttributes(new WorkflowExecutionStartedEventAttributes)
      runs.put(runId, WFState(startWorkflowExecutionRequest, events = List(workflowStart)))
      val runIds: List[String] = runsForDomain.getOrDefault(startWorkflowExecutionRequest.getDomain, Nil) :+ runId
      runsForDomain.put(startWorkflowExecutionRequest.getDomain, runIds)
    }

    run
  }

  override def pollForDecisionTask(pollForDecisionTaskRequest: PollForDecisionTaskRequest): DecisionTask = {
    runs.synchronized {
      val domain: String = pollForDecisionTaskRequest.getDomain
      for {
        run <- runsForDomain.get(domain)
        state = runs.get(run)
        events: List[HistoryEvent] = state.events
        lastEvent = events.lastOption.getOrElse(new HistoryEvent)
        if lastEvent.getDecisionTaskStartedEventAttributes == null && lastEvent.getActivityTaskStartedEventAttributes == null
      } {

        var newEvents = events

        val scheduledEventID: Long = newEvents.length.toLong

        {
          val history: HistoryEvent = new HistoryEvent()
          val attributes: DecisionTaskScheduledEventAttributes = new DecisionTaskScheduledEventAttributes
          attributes.setStartToCloseTimeout("NONE")
          attributes.setTaskList(state.startWorkflowExecutionRequest.getTaskList)
          history.setDecisionTaskScheduledEventAttributes(attributes)
          newEvents = newEvents :+ history
        }

        val startedEventID: Long = newEvents.length.toLong

        {
          val history: HistoryEvent = new HistoryEvent()
          val attributes: DecisionTaskStartedEventAttributes = new DecisionTaskStartedEventAttributes
          attributes.setIdentity("NONE")
          attributes.setScheduledEventId(scheduledEventID)
          history.setDecisionTaskStartedEventAttributes(attributes)
          newEvents = newEvents :+ history
        }

        runs.put(domain, state.copy(events = newEvents))

        val task: DecisionTask = new DecisionTask()
        task.setStartedEventId(startedEventID)
        task.setEvents(newEvents)
        return task
      }
    }

    return null
  }

  override def setEndpoint(endpoint: String): Unit = ???
  override def requestCancelWorkflowExecution(requestCancelWorkflowExecutionRequest: RequestCancelWorkflowExecutionRequest): Unit = ???
  override def countClosedWorkflowExecutions(countClosedWorkflowExecutionsRequest: CountClosedWorkflowExecutionsRequest): WorkflowExecutionCount = ???
  override def shutdown(): Unit = ???
  override def registerWorkflowType(registerWorkflowTypeRequest: RegisterWorkflowTypeRequest): Unit = ???
  override def respondActivityTaskCompleted(respondActivityTaskCompletedRequest: RespondActivityTaskCompletedRequest): Unit = ???
  override def signalWorkflowExecution(signalWorkflowExecutionRequest: SignalWorkflowExecutionRequest): Unit = ???
  override def listClosedWorkflowExecutions(listClosedWorkflowExecutionsRequest: ListClosedWorkflowExecutionsRequest): WorkflowExecutionInfos = ???
  override def respondActivityTaskFailed(respondActivityTaskFailedRequest: RespondActivityTaskFailedRequest): Unit = ???
  override def respondActivityTaskCanceled(respondActivityTaskCanceledRequest: RespondActivityTaskCanceledRequest): Unit = ???
  override def deprecateWorkflowType(deprecateWorkflowTypeRequest: DeprecateWorkflowTypeRequest): Unit = ???
  override def getWorkflowExecutionHistory(getWorkflowExecutionHistoryRequest: GetWorkflowExecutionHistoryRequest): History = ???
  override def countPendingActivityTasks(countPendingActivityTasksRequest: CountPendingActivityTasksRequest): PendingTaskCount = ???
  override def registerDomain(registerDomainRequest: RegisterDomainRequest): Unit = ???
  override def pollForActivityTask(pollForActivityTaskRequest: PollForActivityTaskRequest): ActivityTask = ???
  override def respondDecisionTaskCompleted(respondDecisionTaskCompletedRequest: RespondDecisionTaskCompletedRequest): Unit = ???
  override def setRegion(region: Region): Unit = ???
  override def describeWorkflowExecution(describeWorkflowExecutionRequest: DescribeWorkflowExecutionRequest): WorkflowExecutionDetail = ???
  override def listDomains(listDomainsRequest: ListDomainsRequest): DomainInfos = ???
  override def recordActivityTaskHeartbeat(recordActivityTaskHeartbeatRequest: RecordActivityTaskHeartbeatRequest): ActivityTaskStatus = ???
  override def deprecateActivityType(deprecateActivityTypeRequest: DeprecateActivityTypeRequest): Unit = ???
  override def getCachedResponseMetadata(request: AmazonWebServiceRequest): ResponseMetadata = ???
  override def countOpenWorkflowExecutions(countOpenWorkflowExecutionsRequest: CountOpenWorkflowExecutionsRequest): WorkflowExecutionCount = ???
  override def listOpenWorkflowExecutions(listOpenWorkflowExecutionsRequest: ListOpenWorkflowExecutionsRequest): WorkflowExecutionInfos = ???
  override def countPendingDecisionTasks(countPendingDecisionTasksRequest: CountPendingDecisionTasksRequest): PendingTaskCount = ???
  override def describeActivityType(describeActivityTypeRequest: DescribeActivityTypeRequest): ActivityTypeDetail = ???
  override def terminateWorkflowExecution(terminateWorkflowExecutionRequest: TerminateWorkflowExecutionRequest): Unit = ???
  override def deprecateDomain(deprecateDomainRequest: DeprecateDomainRequest): Unit = ???
  override def listWorkflowTypes(listWorkflowTypesRequest: ListWorkflowTypesRequest): WorkflowTypeInfos = ???
}

class CancelTest extends FlatSpec {
  val rememberer = new Rememberer

  val parser = new InputParser[String] {
    override def serialize(input: String): String = input
    override def deserialize(inputString: String): String = inputString
  }
  private val domain: String = "sswf-tests"
  private val wf: String = "cancel-test"
  private val swf: AmazonSimpleWorkflow = new TestWorkflowEngine
  private val logger: StdOutLogger = new StdOutLogger
  val manager = new WorkflowManagement[String, TestSteps](domain, wf, "0.0", wf, swf, inputParser = parser, log = logger)
  val definition = new CancelTestWorkflowDef(rememberer)
  val actor = new StepActionWorker[String, TestSteps](domain, wf, swf, parser, definition, log = logger)
  val decider = new StepDecisionWorker[String, TestSteps](domain, wf, swf, parser, definition, logger)

  "InProgress activities" should "get cancelled" in {
    manager.registerWorkflow()
    val workflow: WorkflowExecution = manager.startWorkflow("A", "")

    {
      val decisionTask: DecisionTask = untilNotNull(decider.pollForDecisionsToMake())
      val decision: RespondDecisionTaskCompletedRequest = decider.makeDecision(decisionTask)
      assert(decision.getDecisions.exists(d => d.getDecisionType == DecisionType.ScheduleActivityTask.toString))
    }

    {
      val activityTask: ActivityTask = untilNotNull(actor.pollForWork())
      val work: RespondActivityTaskCompletedRequest = actor.doWork(activityTask)
      assert(work.getResult === StepResult.serialize(InProgress(None)))
    }

    manager.cancelWorkflowExecution(workflow.getWorkflowId, workflow.getRunId)

    {
      val decisionTask: DecisionTask = untilNotNull(decider.pollForDecisionsToMake())
      val decision: RespondDecisionTaskCompletedRequest = decider.makeDecision(decisionTask)
      assert(decision.getDecisions.exists((d: Decision) => d.getDecisionType == DecisionType.CancelWorkflowExecution.toString))
      assert(rememberer.toRemember === "cancelled")
    }
  }

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
