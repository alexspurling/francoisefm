## Deploy

Create a new DigitalOcean Droplet providing your SSH public key.

**Create an entry in `.ssh/config`**
```commandline
Host francoisefm
    Hostname 167.172.55.28
    User root
```

### Install Ansible
```commandline
sudo apt install ansible
```

or on MacOS:

```commandline
python -m pip install ansible
```

**Check Ansible version**
```commandline
$ ansible --version
ansible [core 2.12.1]
  config file = None
  configured module search path = ['/Users/alex/.ansible/plugins/modules', '/usr/share/ansible/plugins/modules']
  ansible python module location = /Users/alex/Library/Python/3.9/lib/python/site-packages/ansible
  ansible collection location = /Users/alex/.ansible/collections:/usr/share/ansible/collections
  executable location = /Users/alex/Library/Python/3.9/bin/ansible
  python version = 3.9.4 (default, Jul  3 2021, 15:27:29) [Clang 9.1.0 (clang-902.0.39.1)]
  jinja version = 3.0.3
  libyaml = True
```


**Install the community general collection**
```commandline
ansible-galaxy collection install community.general

```

### Run Ansible

**Set up the UFW firewall**
```commandline
ansible-playbook -i inventory 01-setupfirewall.yml -u root
```
**Create a user**
```commandline
ansible-playbook -i inventory 02-createuser.yml -u root
```
**Install required software**
```commandline
ansible-playbook -i inventory 03-install.yml -u alex
```
**Deploy the site**
```commandline
ansible-playbook -i inventory 04-deploy.yml -u alex
```
