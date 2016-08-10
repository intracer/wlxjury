# -*- mode: ruby -*-
# vi: set ft=ruby :

VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
    config.vm.define "wlxjury", primary: true do |wlxjury|
        wlxjury.vm.box = "ubuntu/trusty64"
    end

    config.vm.provider "virtualbox" do |vb|
        vb.memory = 2048
    end

    config.vm.provision "shell", path: "provision.sh"

    config.vm.network "forwarded_port", guest: 9000, host:9000
    config.vm.network "forwarded_port", guest: 5005, host:5005
    config.vm.network "private_network", type: "dhcp"

    config.vm.synced_folder ".", "/vagrant", type: "nfs"

end