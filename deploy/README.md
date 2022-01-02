## Deploy

Create a new DigitalOcean Droplet providing your SSH public key.

**Create an entry in `.ssh/config`**
```commandline
Host francoisefm
    Hostname 167.172.55.28
    User root
```
**Install Ansible**
```commandline
sudo apt install ansible
```

or on MacOS:

```commandline
cd ~/dev/virtualenvs
pip install virtualenv
python -m virtualenv ansible
source ansible/bin/activate
python -m pip install ansible
```

and then make sure to run this before running the following Ansible commands:

```commandline
source ~/dev/virtualenvs/ansible/bin/activate
```

**Check Ansible version**
```commandline

```


**Install the community general collection**
```commandline
ansible-galaxy collection install community.general
```
**Set up the UFW firewall**
```commandline
ansible-playbook -i inventory 01-setupfirewall.yml -u root
```
**Create a user**
```commandline
ansible-playbook -i inventory 02-createuser.yml -u root
```
**Install nginx**
```commandline
ansible-playbook -i inventory 03-nginx.yml -u alex
```
**Deploy the site**
```commandline
ansible-playbook -i inventory 03-nginx.yml -u alex
```
**Login to Ubuntu server and install LetsEncrypt**

Instructions originate here: https://certbot.eff.org/instructions?ws=nginx&os=ubuntufocal
```commandline
sudo snap install core
sudo snap refresh core
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/bin/certbot
```
