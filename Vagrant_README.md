Vagrant Environment Howto
-------------------------

Pre-Vagrant Setup:

* Install VirtualBox
    e.g. brew cask install virtualbox
* Install Vagrant
    e.g. brew cask install vagrant
* Configure ssh-agent forwarding in order for Puppet to get required modules:
  Ensure ~/.ssh/config contains:
    ```Host 127.0.0.1
          ForwardAgent yes
    ```

  Make sure ssh-agent is running:
  ```ssh-agent```

  Add your local gitssh ssh key to the agent:
  e.g. ```ssh-add ~/.ssh/id_rsa```

* Hostname/IP:
 By default the vagrant instance runs on a host-only network, available on the IP 10.255.0.10.
 Edit your /etc/hosts to assign this the DNS name "local.broadinstitute.org":
  ``` 10.255.0.10    local.broadinstitute.org ```
  This is so the SSL certificates created for the local environment remain 'vaild'

Environment Setup:
 Two environmental variables are needed for the vagrant process to connect to vault correctly:

 * in bash: Set the environment (used to fetch secrets from vault)
  ```export ENVIRONMENT="local"```

 * in bash: Set your vault token.  You will request this token from devops.
 ```export VAULT_TOKEN="some_token_string"```

Bring up the vagrant instance:

``` vagrant up ```

If this is the first time bringing up the instance, the base box must be downloaded. Once this is done (one time) the instance will be created, booted and provisioned.
Any ```*.ctmpl``` files in the root of the repo will be filled in via consul-template, and copied by default to /etc (ie, app.conf.ctmpl -> /etc/app.conf). 

* Port Forwarding:
  If you need additional ports forwarded into the VM, edit the VagrantFile accordinly and run ``` vagrant reload ``` 



Login:

``` vagrant ssh ```
You now have a shell inside the instance. The repo you ran this from is live-mirrored/mounted into ```/vagrant``` 


While inside the VM:

Docker authentication: 
If you wish to preform any docker actions that require authentication (push/pulling from private repos, such as openidc-proxy), you must authenticate:
``` docker login ```

To start Rawls:
``` docker-compose -f /etc/rawls-compose.yaml -p rawls up ```

Reprovision:

If for some reason you need to/want to reprovision:
``` vagrant provision ```

Port Forwards


NOTE: You may have problems accessing the local rawls instance if you are connected via a VPN