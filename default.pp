include docker

class {'python':
  version     => 'system',
  pip         => 'latest',
}
python::pip { 'docker-compose':
  ensure => latest,
}
class { 'consul_template':
  download_url    => 'https://github.com/hashicorp/consul-template/releases/download/v0.10.0/consul-template_0.10.0_linux_amd64.tar.gz',
  vault_enabled   => true,
  vault_address   => 'https://clotho.broadinstitute.org:8200',
  service_enable  => false,
  service_ensure  => stopped,
}

staging::deploy { 'vault.zip':
  source  => 'https://dl.bintray.com/mitchellh/vault/vault_0.2.0_linux_amd64.zip',
  target  => '/usr/local/bin/',
  creates => '/usr/local/bin/vault',
}
