# 00 - Initial Setup

Let's install a lot of things.

## Installing a lot of things

* Java 1.8: http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html
* Scala. You'll need at least the version defined in [build.sbt](../build.sbt), under `scalaVersion`: http://www.scala-lang.org/
* sbt, the scala build tool: http://www.scala-sbt.org/
* IntelliJ: https://www.jetbrains.com/idea/
  * Install the Scala plugin from `Settings > Plugins`
* Docker: https://www.docker.com/
  * For Mac, install the [Docker Native client](https://www.docker.com/products/docker#/mac)
* git

## First time setup

### Pulling the repo

From your command line:

* Pull the repo from GitHub
* Check out this rampup branch
* Make your own branch and push it back to GitHub

```
git clone git@github.com:broadinstitute/rawls.git
cd rawls
git fetch
git checkout -t origin/rampup
git checkout -b YOURINITIALS_rampup
git push -u origin YOURINITIALS_rampup
```

To open the project in IntelliJ, launch IntelliJ and open `build.sbt`.

### Setting up configuration files

Go back up a directory in your terminal:

```
git clone git@github.com:broadinstitute/firecloud-develop.git
```

Then follow the instructions over at the [firecloud-develop](https://github.com/broadinstitute/firecloud-develop) repo. Rawls relies on a bunch of configuration files that are built from data stored in our secret managing service, Vault.

You'll need to do the following (all documented on the front page of [firecloud-develop](https://github.com/broadinstitute/firecloud-develop)):

* Generate a GitHub personal access token
* Authenticate to Vault
* Run `./configure.rb`
  * You'll probably want to set the output directory to `/etc`. If you don't, you'll need to symlink a few files in that directory.

Parts of this process will probably be wrong. Please poke someone and update the documentation so it's right!

### Running tests and starting up Rawls

Back in the Rawls directory, you should now be in a position to run tests:

```
docker run -p 3306:3306 --name mysql -e MYSQL_ROOT_PASSWORD=rawls-test -e MYSQL_USER=rawls-test -e MYSQL_PASSWORD=rawls-test -e MYSQL_DATABASE=testdb -d mysql/mysql-server:5.7
sbt clean compile test -Dmysql.host=`docker-machine ip default`
```

You may need to bump up the amount of memory sbt uses: if you get out of memory errors, try `sbt -mem 4096 ...` instead.

Then, spin up Rawls using the instructions [here](https://github.com/broadinstitute/firecloud-develop#running-a-service-locally). You should now be able to visit https://localhost and see Swagger running.

### Trying it out

For development purposes you'll need a [fresh new gmail account](https://accounts.google.com/SignUp) to use with Workbench. This is because Workbench stores a token associated with your Google login that allows it to operate on your behalf -- and developers have access to those tokens (but not in the Production environment, where it *is* safe to use your work email address).

Let's get your user registered. Head on over to [the Firecloud dev environment](firecloud.dsde-dev.broadinstitute.org) (only accessible from behind the Broad firewall) and sign up. Now your user exists in the dev database, we can go back and use your local Rawls.

Back on your [local Swagger page](https://localhost), open `workspaces` and hit `GET /api/admin/workspaces`. Click the little slider that currently says OFF, tick all the boxes, and then hit `Authorize`. You'll then be taken to a sign-in for your new gmail account, and dumped back to Swagger afterwards. You are now magically OAuthed to use Swagger (for an hour; then you'll have to do it again). You can then hit `Try it out!` and look at the pretty list of workspaces.

# Scala reading

If you're completely new to Scala, there are a few things we recommend:

http://danielwestheide.com/scala/neophytes.html
https://twitter.github.io/scala_school/ scala school

Ready to write some code? [Onward!](01 - Ping.md).
