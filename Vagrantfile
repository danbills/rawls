
Vagrant.configure(2) do |config|
  #Use centos7 for box to match gce instances
  #config.vm.box = "centos/7"
  #centos/7 doesn't have virtual box tools
  config.vm.box = "landregistry/centos"

  #Config vm "hardware"
  config.vm.provider "virtualbox" do |v|
    v.memory = 1024
    v.cpus = 2
  end

  #static, private IP address
  config.vm.network "private_network", ip: "10.255.0.10"

  #Port Forwards
  #config.vm.network "forwarded_port", guest: 80, host: 8080
  config.vm.network "forwarded_port", guest: 443, host: 8443
  config.vm.network "forwarded_port", guest: 5050, host: 50505
  #config.vm.network "forwarded_port", guest: 8080, host: 8880
  #config.vm.network "forwarded_port", guest: 8081, host: 8881

  #Simple hostname
  config.vm.hostname = "local.broadinstitute.org"

  #Forward agent for ssh keys etc (github access, etc)
  config.ssh.forward_agent = true

  #Sync folder
  config.vm.synced_folder ".", "/vagrant", type: "virtualbox"

  #Bootstap
  config.vm.provision "shell", path: "vagrant_bootstrap.sh"

  #Render templates from vault
  config.vm.provision "shell", path: "render_templates.sh", args: "#{ENV['ENVIRONMENT']} #{ENV['VAULT_TOKEN']}"
end
