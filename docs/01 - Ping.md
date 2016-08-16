# 01 - Ping!

## Exercise

To test whether Rawls is up, we'd like to add a `ping` endpoint.

* It should live at `/api/ping`
* When called, it should return the current timestamp

You should also write a test that confirms the timestamp is valid and less than a few seconds old.

Start off by making a branch for this exercise:

```
git checkout -b YOURINITIALS_rampup_01
```

We'll make a pull request from this branch into your main rampup branch once the exercise is done.

## Things to think about

* `RawlsApiService.scala` is where calls to the endpoints are received - look for `def receive`.
* Some of the endpoints are defined in other files. Where are they? How are they referenced?
* Do a search for the function `listWorkspaces()`. What's the path from `def receive` to the function getting called?
* For this task you can just add the endpoint to `RawlsApiServiceActor`.
* You'll also want to add a test in `RootRawlsApiServiceSpec.scala`.
* And finally, you'll want to add documentation of this endpoint to Swagger, in `rawls.yaml`.
  * Documentation for Swagger is [here](http://swagger.io/getting-started-with-swagger-i-what-is-swagger/), and the spec is [here](http://swagger.io/specification/).
  * The spec is quite a dry read. You can probably figure it out using the existing Swagger defintions as examples.

## Committing and pushing to GitHub

You should probably have made some commits along the way! Not to worry if you hadn't; we'll do that now. At your terminal:

```
git status
```

will tell you which files have been modified. You can then add them to git's staging area using wildcards or their actual paths:

```
git add *.scala
git add src/main/resources/swagger/rawls.yaml
```

Commit your changes with a helpful message:

```
git commit -m "Added /api/ping endpoint"
```

And push them up to GitHub:

```
git push
```

## Opening a Pull Request

Head over to the [Rawls repo on GitHub](https://github.com/broadinstitute/rawls). You'll probably see a notification at the top saying "You recently pushed branches" and then your branch - if you do hit the green "Compare & pull request" button. If not, just hit the "New pull request button".

Pull requests are how we get code from your branch - where you've been working on your feature in isolation - into the main development branch. It gives other developers an opportunity to review your changes before they get merged. Most of our repos will automatically populate a pull request template full of checkboxes that guide you through the PR process.

Change your base branch to the `YOURINITIALS_rampup` you made ages ago, and the compare branch to `YOURINITIALS_rampup_01` that you made for this exercise. Name the PR something sensible - when you're working on tickets in JIRA, you should put the issue number here along with a brief description.

### The Pull Request process

Go ahead and open the pull request. You should see all your commits and their changes in a diff view. The checkboxes will walk you through what to do next, but the important ones:

* Pull requests are reviewed by at least two people: the project's tech lead, and one other developer chosen to be Lead Reviewer. The tech lead will usually give the PR a once-over; the LR has the main responsibility for reviewing.
* You can choose the LR yourself if you think a particular person would be best suited. Otherwise, you can [spin the wheel](http://broad.io/foobarwheel) to pick someone at random!
* The tech lead and LR might give you feedback that require more changes. You can add them as new commits to the branch, push it to GitHub again, and that will automagically update the PR.
* Once the tech lead and LR have given you a thumbs up, you're good for the final run through of checkboxes. These are basically "make sure the tests go green" and "hit the merge button". Note that GitHub will squash your commits for you if you ask it to (which you should).

# Onward!

Congratulations - you've done your first feature! The next exercises will take you on a tour through the codebase.
