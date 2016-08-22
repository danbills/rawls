# todo

List of things to cover.

## in progress

- bucket deletion monitor

## the rest

- figure out which order to do these in

### outstanding bits of code to delete

- bucket deletion monitor
  - BucketDeletionMonitor.scala, bits in BootMonitors and HttpGoogleDAO
  - move the pendingBucketDeletionQuery updates from BucketDeletionMonitor to HttpGoogleDAO
  - make an explicit note after `executeGoogleRequest(deleter)` that the following line may never execute if the request throws an exception

### tech tasks

encourage TDD with these.

- add a set attribute type
  - ...and write the database migration script!
- expression parsing: implement literals
- implement endpoint to evaluate an expression on a single entity
- multi cromwells
- abort a submission
  - compare and contrast the obvious solution (forall workflows cromwell.abort) to what we actually do
- fix a bug (drop a future on the floor?)
- ...

### scala patterns

- withFoo
- RequestComplete
- Option, Try (bonus: either) --> link to that blog about these
- Futures
- map vs flatmap (in the context of futures, also other monads)
- actors
- slick
  - raw sql
- talking to external services
- ...


