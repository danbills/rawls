# todo

List of things to cover.

## in progress

- bucket deletion monitor

### outstanding bits of code to delete

- bucket deletion monitor
  - BucketDeletionMonitor.scala, bits in BootMonitors and HttpGoogleDAO
  - move the pendingBucketDeletionQuery updates from BucketDeletionMonitor to HttpGoogleDAO
  - make an explicit note after `executeGoogleRequest(deleter)` that the following line may never execute if the request throws an exception

## the rest

The idea here is to weave lessons about the Scala patterns in with the tech tasks. A good task is well-contained, doesn't touch many parts of the codebase, and demonstrates one - *maybe* two - of the Scala patterns.

### tech tasks

some suggestions for tasks

- implement endpoint to evaluate an expression on a single entity
  - i.e. reimplement `WorkspaceService.evaluateExpression`
- expression parsing: implement literals
  - or maybe: remove the current literal expressions and implement [GAWB-1117](https://broadinstitute.atlassian.net/browse/GAWB-1117)
- fix a bug (drop a future on the floor?)
- abort a submission
  - compare and contrast the obvious solution (forall workflows cromwell.abort) to what we actually do
- multi cromwells
- add a set attribute type
  - ...and write the database migration script!
- ...

### scala patterns

- withFoo
- actors
- perRequest (= one actor per request)
- RequestComplete
- Option, Try (bonus: either) --> link to that blog about these
- Futures
- map vs flatmap (in the context of futures, also other monads)
- slick
  - raw sql
- talking to external services
- ...
