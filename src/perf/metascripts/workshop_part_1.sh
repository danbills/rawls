#!/bin/bash

#TEST: do a list
gatling.sh -m -s default.listWorkspaces

#ditch previous cloneWorkspaces lists
rm ../user-files/data/cloneWorkspaces*.tsv

#TEST: clone a standard workspace a bunch of times
gatling.sh -m -s default.cloneWorkspaces

#cloneWorkspaces spits out a tsv of workspace names it created. pass to future calls
wspaces=$(find ../user-files/data/ -name cloneWorkspaces_NAMES_*.tsv)

#TEST: launch a standard job on each of the workspaces
gatling.sh -m -s default.launchAnalysis -DworkspaceList="$wspaces"

#TEST: monitor submissions on each of the workspaces
#TODO: monitor job DSDEEPB-2109
