#!/bin/bash

rm ../user-files/data/createWorkspaces*.tsv

gatling.sh -m -s default.createWorkspaces

#createWorkspaces spits out a tsv of workspace names it created. pass to future calls
wspaces=$(find ../user-files/data/ -name createWorkspaces_NAMES_*.tsv)

gatling.sh -m -s default.importTSV -DworkspaceList="$wspaces"

#TODO: submit job DSDEEPB-2108
#TODO: monitor job DSDEEPB-2109
