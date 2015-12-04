# Running tests in Gatling

1. Download Gatling: http://gatling.io/#/download
2. Point your gatling.conf located in {GATLING_HOME}/conf to use the bodies, data, and simulation in {RAWLS_HOME}/src/perf
	Locations are configurable under "directory"

	It should look like:

	    directory {
          data = {your_rawls_home}/src/perf/data
          bodies = {your_rawls_home}/src/perf/bodies
          simulations = {your_rawls_home}/src/perf/simulations
          #reportsOnly = ""
          #binaries = ""
          #results = results
        }

3. Create a plaintext config.txt file in your {GATLING_HOME}/user-files which is of the following form:

	{YOUR_ACCESS_TOKEN}
	{NUM_USERS}

	Example:

	ya29.ZZZZZZ_zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz
    100

4. Run ./gatling.sh in located in {GATLING_HOME}/bin
5. A list of valid simulations will pop up. Select one that excites you.
6. Let it run.

Other things to note:

- If you want, you can view the list of JSON bodies that were posted for createWorkspaces and cloneWorkspaces.

- The TSV import can use any TSV you want, just point the simulation to use it in the line:
	.bodyPart(RawFileBodyPart("entities", "YOUR_TSV_FILE_PATH").contentType("text/plain")))

- The TSV import and submission launcher simulations feed off of a list of workspace names. These need to already exist. example_workspaceName_list.tsv (included in this branch) is an example of what this looks like. It's a 1 column TSV with header "workspaceName" followed by a list of workspace names, 1 per line.

- You may also want to change the timeout limits on rawls and in gatling.conf. Gatling defaults to 60000ms. For gatling.conf, increase all timeouts under "ahc" as you see fit.