Getting Started
===============

Simple Workflow is not simple. Let's really simplify it!

Here's how it works:

1. Define an Enum capturing all the potential steps of your workflow. These contain metadata about timeouts, etc.
2. Define a "Steps" class that returns a list of steps to execute. You get to look at the workflow input, but 
   not the history. The idea is that you define a workflow that's easy to predict and consists of a series of steps
   to completion.
3. Define a "Controller" class that specifies the behaviour for each step.
4. Register and start your workflow using a "WfService" class

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

          - needsExtract := decideWhetherItsTimeForAnEXTRACT():
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

Working example
---------------

Run the example with `sbt run` or through IDEA by running `ExampleWorkflowService`.
Note that you must have AWS credentials in your env: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_KEY`, and `AWS_SECRET_ACCESS_KEY`, which is the same as `AWS_SECRET_KEY`.

The example code is in `src/main/java/example`. This is everything you'll need to do to get up and running!

              

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
# to run
sbt run 
# to package
sbt packageLocal #(etc...)
```

Protip: Use a tilde (`sbt ~test`) to have sbt monitor the files and re-execute the task when anything changes.


To cross-build for scala 2.10 and scala 2.11 (note the '+'):

```
sbt +compile
# to package
sbt +packageLocal #(etc...)
```
