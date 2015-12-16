#!/bin/bash

rm ../user-files/data/createWorkspaces*.tsv

gatling.sh -m -s default.createWorkspaces

#createWorkspaces spits out a tsv of workspace names it created. pass to future calls
wspaces=$(find ../user-files/data/ -name createWorkspaces_NAMES_*.tsv)

gatling.sh -m -s default.importTSV -DworkspaceList="$wspaces"

#TEST: import a standard method config into each of the workspaces
gatling.sh -m -s default.importMethodConfigs -DworkspaceList="$wspaces"

#TEST: launch a standard job on each of the workspaces
gatling.sh -m -s default.launchAnalysis -DworkspaceList="$wspaces"

#TEST: monitor submissions on each of the workspaces
#TODO: monitor job DSDEEPB-2109
