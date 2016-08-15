# todo

List of things to cover.

1. Initial setup
  - install scala, intellij, git, docker, ...
  - either make their own git branch off rampup, or fork the entire project to their personal github, then set rampup as default
  - open build.sbt in intellij
  - start up rawls and look at swagger. try something simple
  - run tests
2. My First Commit
  - a small no-brainer task: implement ping as "return current timestamp"
  - write a test for it
  - gitflow: branch and PR process
  - actually make the PR and get someone to review it!

  
### tech tasks

encourage TDD with these.

- add a set attribute type
  - ...and write the database migration script!
- expression parsing: implement literals
- implement endpoint to evaluate an expression on a single entity
- multi cromwells
- bucket deletion monitor
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
- talking to external services
- ...


