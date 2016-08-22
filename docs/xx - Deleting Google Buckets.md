# xx - Deleting Google Buckets

When the user deletes a workspace, we also delete the Google bucket associated with it. Unfortunately, Google doesn't allow you to delete a bucket that has items in it, and it'd be prohibitively slow to list all bucket items and delete them one by one.

While Google doesn't have a "delete everything in this bucket" API call, it *does* have a call to set up [lifecycle management](https://cloud.google.com/storage/docs/lifecycle) on objects in the bucket. Using lifecycle rules, you can specify that Google should auto-delete all objects older than a certain age. Like zero.

Your predecessor has written some code in `HttpGoogleServicesDAO.scala` that does this, but we've discovered that Google only applies the lifecycle rules once a day - so this won't happen instantaneously. The code currently retries until it's successful, but if we restart Rawls, we'll forget that we were retrying, and the bucket will never get deleted.

## Exercise

Write a monitor actor that queries the database at startup for in-progress bucket deletions and restarts the process of trying to delete them.

Once you're done, submit a pull request as usual.

## Things to think about

* Your predecessor has already added a `PendingBucketDeletionComponent` and associated database calls to log which buckets we're in the process of trying to delete (see `deleteBucket` in `HttpGoogleServicesDAO.scala`).
* Monitor actors such as this are started in `BootMonitors.scala`.
  * `startSubmissionMonitor` is a good place to crib from. The submission monitor handles querying Cromwell for the status of active submissions and similarly needs relaunching after a restart.
  * Where is the `submissionSupervisor` created?
* How will your new monitor interact with `deleteBucket`? How might you need to change `deleteBucket` to support this?

# Related reading

If you would like to read too much information on Akka Actors, try [here](http://doc.akka.io/docs/akka/current/scala/actors.html). The short version:

* An Actor is an object that lives in its own thread.
* Communication between Actors uses messages: `someActor ! MyMessageType(some, message, parameters)` sends a message of `MyMessageType` to `someActor`.
* You can look up Actors by name using `system.actorSelection()`, but this is slow; wherever possible, you want to pass a reference to your actor into the function or class that wants to send messages to it.
* Actors receive messages in their `def receive` function, and decide what to do with them there.
  * You've seen this before: remember that call to `listWorkspaces()` you looked at back in the Ping exercise? `WorkspaceApiService` sends a `ListWorkspaces` message to `WorkspaceService` (hidden inside the `perRequest` call), which turns it into `listWorkspaces()` call in its `def receive`.

