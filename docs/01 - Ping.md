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
