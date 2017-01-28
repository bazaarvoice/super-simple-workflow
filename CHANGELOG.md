# Change Log

All notable changes to this projected will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).
This changelog follows [Keep a CHANGELOG](http://keepachangelog.com/).

## 6.2 - 2017-01-28
* move slf4j-simple dependency to the example module so it doesn't get exported as a transitive dependency

## 6.1 - 2017-01-26
* bugfix: paging over activity types was broken. See https://github.com/bazaarvoice/super-simple-workflow/pull/22

## 6.0 - 2017-01-02
* simplify the step interface by consolidating heartbeat and step timeouts.
** now, you just have to either complete the step or call checkIn within the timeout to keep the step alive.
* clean up the example's shutdown sequence so it actually exits.
* moved project to Scala 2.12
* dropped support for Scala 2.10 and 2.11
** We treat compiler warnings as errors, so moving to 2.12 forced us to use JavaConverters instead of JavaConversions.
   Sadly, This results in code that doesn't compile in 2.10 and 2.11.

## 5.2 - 2016-12-29
* create new `sswf-guava-20` module with a few convenience classes defining
  the decision and action services
* modularize the project (pull the example code from the main artefact)
* move integration tests to an integration testing task (TravisCI builds now pass)
** Note that all of our tests are integration tests, so the "sbt test" task
   now does nothing. We have https://github.com/bazaarvoice/super-simple-workflow/issues/20
   open to track the transition from integration tests to unit tests.

## 5.1 - 2016-12-29
* burned a version number messing with the project structure

## 5.0 - 2016-12-06
* new APIs for listing open and closed workflow executions

## 4.1 - 2016-10-04
* bugfix: Assertion would fail under some conditions when workflow steps return InProgress. This fix clarifies and unifies when workflow steps are considered "the same" by SSWF.

## 4.0 - 2016-09-15
* change in-progress wait timer from scalar to function

## 3.2 - 2016-06-03
* add ability to terminate workflows

## 3.1 - 2016-05-17
* stop logging errors. Instead, use info when logging is appropriate and throw exceptions when that is more appropriate.

## 3.0 - 2016-04-14
* bugfix: cancel workflows when steps return InProgress or time out
* Colon (":") is now allowed in step response messages
* Null byte ("\u0000") is now allowd in workflow and step inputs
* StepActionWorker and WorkflowManagement new require a logger at construction.
  The advantage is that they now log errors when they happen and also provide some debug logs
* 2.2 bugfix was in error. Instead, require result to be less than 32768 before sending.

## 2.2 - 2016-04-13
* bugfix: limit the ActivityTaskCompleted result we send to SWF to 32768 characters

## 2.1 - 2016-04-06
* bugfix: invoke WorkflowDefinition#onCancel when workflow is cancelled.

## 2.0 - 2016-03-24
* Support for canceling workflows gracefully [#11](https://github.com/bazaarvoice/super-simple-workflow/issues/11).
  This required a small change in the api (addition of onCancel hook), hence the major version bump.

## 1.0 - 2016-03-10
* Add support for signals [#4](https://github.com/bazaarvoice/super-simple-workflow/issues/4) (see Waiting on Signals in the README).
* Accordingly, a new step response, `Wait()`, is added.
* Some of the convenience constructors for step responses are removed.
* There is a new restriction that step response messages cannot contain a colon (":").

## 0.9 - 2016-03-09
* Added the changelog.
* Bugfix: step duration was cumulative instead of only measuring the duration of the current run of the step
* Bugfix: SleepStep timers were reporting "SUCCESS:Started" instead of simply "STARTED"
