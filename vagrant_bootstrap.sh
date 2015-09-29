#!/bin/bash

sudo yum -y --nogpgcheck localinstall https://yum.puppetlabs.com/puppetlabs-release-el-7.noarch.rpm
sudo yum -y install rubygems git puppet unzip
gem install librarian-puppet

cd /vagrant

/usr/local/bin/librarian-puppet install

sudo puppet apply --verbose --modulepath=modules/ default.pp

sudo rm -f /etc/localtime
sudo ln -s /usr/share/zoneinfo/America/New_York /etc/localtime

#App specific things go here (ie, copy a config to a place, etc etc)
sudo cp -v site.conf /etc/
