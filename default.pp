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

#We don't want the ctmpls for ssl certs IN the repo, since we don't want them
#to be filled in in deployed instances.
#Create the templates here.
$local_ssl_server_crt_vault_path="secret/dsde/local/local_broadinsititute_org"

file {'/vagrant/server.crt.ctmpl':
  content => "{{with \$secret := vault \"${local_ssl_server_crt_vault_path}\" }}{{\$secret.Data.server_crt}}{{end}}",
  owner   => 'root',
  group   => 'root',
  mode    => '0444',
}
file {'/vagrant/server.key.ctmpl':
  content => "{{with \$secret := vault \"${local_ssl_server_crt_vault_path}\" }}{{\$secret.Data.server_key}}{{end}}",
  owner   => 'root',
  group   => 'root',
  mode    => '0400',
}
file {'/vagrant/ca-bundle.crt.ctmpl':
  content => "{{with \$secret := vault \"${local_ssl_server_crt_vault_path}\" }}{{\$secret.Data.ca_bundle_crt}}{{end}}",
  owner   => 'root',
  group   => 'root',
  mode    => '0444',
}
