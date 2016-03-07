# Change Log

All notable changes to this projected will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).
This changelog follows [Keep a CHANGELOG](http://keepachangelog.com/).

## 0.9 - 2016-03-09
* Added the changelog.
* Bugfix: step duration was cumulative instead of only measuring the duration of the current run of the step
* Bugfix: SleepStep timers were reporting "SUCCESS:Started" instead of simply "STARTED"