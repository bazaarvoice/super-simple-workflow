[![Join the chat at https://gitter.im/super-simple-workflow/Lobby](https://badges.gitter.im/super-simple-workflow/Lobby.svg)](https://gitter.im/super-simple-workflow/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/bazaarvoice/super-simple-workflow.svg?branch=master)](https://travis-ci.org/bazaarvoice/super-simple-workflow)

Getting Started
===============

Simple Workflow is not simple. Let's really simplify it!

Here's how it works:

1. Define an `InputParser` class that tells sswf how to serialize your workflow input.
2. Define an Enum capturing all the potential steps of your workflow (extending `WorkflowStep`). These contain metadata about timeouts, etc.
3. Define a `WorkflowDefinition` class:
    * The `workflow` method returns a list of steps to execute. You get to look at the workflow input, but not the history. 
      The idea is that you define a workflow that's easy to predict and consists of a series of steps to completion.
    * The `act` method specifies the behaviour for each step.
4. Register and start your workflow using the `WorkflowService` class

One big thing that sticks out here is that your workflow is just a sequence of steps. There's no branching or conditional execution of steps.
In our experience, most workflows wind up being almost, if not entirely, linear. Deciding to just support that use case means that we can make it
drop-dead simple.

If you're thinking that you need branching because some states don't need to execute sometimes, you can achieve the same effect by just having
those states say `Success("Nothing to do!")`. You would only really need branching if you wanted to do one of two totally different things based on the 
result of some step.

Working example
---------------

Run the example with `sbt example/run` or through IDEA by running `ExampleWorkflowService`.
Note that you must have AWS credentials in your env: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_KEY`.

Your AWS credentials must have access to at least the following [SWF actions](http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-api-by-category.html):

- PollForActivityTask
- RespondActivityTaskCompleted
- RecordActivityTaskHeartbeat
- PollForDecisionTask
- RespondDecisionTaskCompleted
- RequestCancelWorkflowExecution
- StartWorkflowExecution
- SignalWorkflowExecution
- TerminateWorkflowExecution
- RegisterActivityType
- RegisterWorkflowType
- RegisterDomain
- RequestCancelWorkflowExecution
- TerminateWorkflowExecution
- ListActivityTypes
- DescribeWorkflowType
- ListOpenWorkflowExecutions
- ListClosedWorkflowExecutions
- GetWorkflowExecutionHistory
- DescribeDomain

The example code is in `example/src/main/java/example`. This is everything you'll need to do to get up and running!


Managing Versions and Names of Things
---------------------------

When you're dealing with SSWF, you have to contend with domains, workflows, task lists, steps, and versions of workflows and steps.
So, what's the right way to think about and use these things?

* A *Domain* is just a handy way to group workflows. You probably just want one domain per project, even if your project has several workflows.

* A *Workflow* is the logical high-level task you're trying to accomplish. So, if you have an ETL job to do, that's your workflow. You may start out with
  3 steps and then add more later, but it's still the same workflow.

* But if you add more steps, then the old workflow workers (who were compiled against the 3-step version) won't have a clue what to do with the new steps
  (read: `IllegalStateException`). This is where the `Workflow Version` comes in. Each worker is polling SWF for a specific version of the workflow, so 
  as long as you bump the version whenever you change the workflow, you'll be golden.

* The *Steps* are the (ahem) individual steps of the workflow. They are really just messages that the sswf workers will use to invoke your `WorkflowDefinition#act` method.
  We could use strings, but you define them in an enum so there is a clear symbolic way to reference them in code. They also contain a small amount of configuration,
  like how long sswf should let the step run before timing it out.
  
* There are also *Task Lists*, which let you restrict workflow executions to particular environments. For example, you may have different data centers 
  (like in the US and the EU), with different data in each data center. Let's say there's a particular workflow to run on new data being put into a datacenter. 
  If new data is added to the EU center, you don't want the US worker picking up parts of the workflow. So, you'll have two task lists: one for the US and one for the EU.
  This can also be useful for confining workflow executions to dev, test, or prod environments.


Writing Steps
--------------

Every time a step executes, it returns one of three results: Success, Failure, or InProgress. They can all contain
messages for reporting later.

The point here is simplicity. Make each Step idempotent, so if a Step takes an action and needs to wait for completion,
make the Step check for completion _first_, returning one of the three results, and _then_ evaluate if the step needs
to execute. If so, execute it and return InProgress so the workflow will call the Step again.

This way, each step enforces an invariant rather than performing an action. You will have the comfort of knowing that
it's always safe to re-start a workflow, since anything that doesn't need to run will simply pass through and anything
in progress will wait for completion.

Example:
        
        ExtractStep:
          - extractState := getExtractState()
          - if extractState.defined && extractState.isRunning:
              return new InProgress("EXTRACT is running: " + extractState.status)
          - if extractState.defined && extractState.isFailed:
              return new Failure("EXTRACT failed: " + extractState.errorMessage)
          - if extractState.defined && extractState.isComplete:
              return new Success("EXTRACT done: " + extractState.summary)

          - needsExtract := decideWhetherWeNeedAnExtract():
          - if needsExtract:
              startExtract()
              return new InProgress("Started EXTRACT")
          - else:
              return new Success("Nothing to do!")

          - in case of any error:
              return new Failure("Unexpected error: " + error.message)
              
Notice how the ExtractStep code will always do its job without corrupting the system no matter how many times it's called
and independent of whatever else has happened. This gives you the power to run, or re-run, the workflow any time
and however often you like without worrying that it will do the wrong thing.

Waiting on Signals
------------------

Sometimes, you ned a workflow to wait for some other system (or person) to complete an action. You can accomplish
this by creating a signal, passing it to that other party, and then they can give it back to you when they are done.

The first step in using signals is to make a signal token by calling `WorkflowManagement#generateSignal()`. 
This gives you back a token you can wait on and which you can also use to send a signal.
You will pass these signals on to the process that you intend to signal you. 

A workflow step can wait on one or more of these signals by returning `new Wait(WAIT_TIMEOUT,SIGNAL_TOKEN)`.

The most convenient way to send the signal is implemented in `WorkflowManagement#signalWorkflow(SIGNAL_TOKEN)`.
You could, for example, set up a web endpoint at `POST http://myservice/signal/{SIGNAL_TOKEN}`, which
delegates to `signalWorkflow(SIGNAL_TOKEN)`.

If sending the signal from a JVM process is inconvenient, you can extract the workflowId, runId, and signalName
from the token and just use the [SWF Signal API](http://docs.aws.amazon.com/amazonswf/latest/developerguide/swf-dg-signals.html)
directly. Here is the format of the tokens generated by `generateSignal()` (pipe delimited):

    WORKFLOW_ID|RUN_ID|SIGNAL_NAME
    
To send a signal, that is all the information you need. The `input` field
of AWS signals is currently unused.

Development
===========

set up your access to the BV maven repo:
--------------------

1. For starters, install sbt.
1. Create a file ```~/.sbt/repositories```:

    ```
    [repositories]
      local
      bazaarvoice: https://repo.bazaarvoice.com:443/nexus/content/groups/bazaarvoice
    ```
    
1. Create a file ```~/.sbt/.credentials```:

    ```
    realm:Sonatype Nexus Repository Manager
    host:repo.bazaarvoice.com
    user:LDAP USERNAME
    password:LDAP PASSWORD
    ```

Working with IDEA
--------------------

The built-in scala and idea support should do you just fine.

build the project:
--------------------

```
sbt compile
# to run samples
sbt example/run 
# execute unit, integration, and end-to-end tests
sbt test-all
# to package
sbt publishLocal #(etc...)
```

Protip: Use a tilde (`sbt ~test`) to have sbt monitor the files and re-execute the task when anything changes.


To cross-build for scala 2.10 and scala 2.11 (note the '+'):

```
sbt +compile
# to package
sbt +publishLocal #(etc...)
```
