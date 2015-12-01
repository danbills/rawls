# Running tests in Gatling

1. Download Gatling: http://gatling.io/#/download
	- Interim step: Copy the simulation files from the /src/perf folder into {GATLING_HOME}/simulations/default. Ideally this will be set to point to the perf folder in the rawls directory, so you can simply git pull to get new simulations.
2. Run ./gatling.sh in located in {GATLING_HOME}/bin
3. A list of valid simulations will pop up. Select the one of interest to you.
4. Let it run.