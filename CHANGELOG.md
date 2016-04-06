# Change Log

All notable changes to this projected will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).
This changelog follows [Keep a CHANGELOG](http://keepachangelog.com/).

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
