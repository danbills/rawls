#!/bin/bash

gatling.sh -m -s default.listWorkspaces

#ditch previous cloneWorkspaces lists
rm ../user-files/data/cloneWorkspaces*.tsv

gatling.sh -m -s default.cloneWorkspaces

#cloneWorkspaces spits out a tsv of workspace names it created. pass to future calls
wspaces=$(find ../user-files/data/ -name cloneWorkspaces_NAMES_*.tsv)

# -DworkspaceList="$wspaces"
#TODO: submit job DSDEEPB-2108
#TODO: monitor job DSDEEPB-2109
