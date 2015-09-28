#!/bin/bash

sudo yum -y --nogpgcheck localinstall https://yum.puppetlabs.com/puppetlabs-release-el-7.noarch.rpm
sudo yum -y install rubygems git puppet unzip
gem install librarian-puppet

cd /vagrant

/usr/local/bin/librarian-puppet install --verbose

sudo puppet apply --verbose --modulepath=modules/ default.pp
