#!/bin/bash

ENVIRONMENT=$1
VAULT_TOKEN=$2
INPUT_PATH=${3-"/vagrant"}
CONSUL_TEMPLATE_PATH=${4-"/usr/local/bin"}
CONSUL_CONFIG=${5-"/etc/consul-template/config/config.json"}
OUT_PATH=${6-"/etc"}
LOG_LEVEL=${7-"err"}

for i in `ls ${INPUT_PATH}/*.ctmpl`; do
  output=`basename ${i%%.ctmpl}`
  echo "Rendering ${i} to ${OUT_PATH}/${output};"
  sudo ENVIRONMENT=$ENVIRONMENT VAULT_TOKEN=$VAULT_TOKEN ${CONSUL_TEMPLATE_PATH}/consul-template -once -config=${CONSUL_CONFIG} -log-level=${LOG_LEVEL} -template=$i:${OUT_PATH}/${output}
done
