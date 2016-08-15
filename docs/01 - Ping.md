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

Head over to the [Rawls repo on GitHub](https://github.com/broadinstitute/rawls). You'll probably see a notification at the top saying "You recently pushed branches" and then your branch - if you do hit the green "Compare & pull request" button.

If not, just hit the "New pull request button". Change your base branch to the `YOURINITIALS_rampup` you made ages ago, and the compare branch to `YOURINITIALS_rampup_01` that you made for this exercise. You should see all your commits and changes. Go ahead and open the pull request.

todo:

- go through the tickboxes
- actually make someone review it

onwards!