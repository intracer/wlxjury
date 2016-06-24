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

end