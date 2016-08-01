#!/bin/bash

DB_NAME=wlxjury_db
DB_USER_NAME=wlx_jury_user
DB_USER_PASSW=wlx_jury

function package_absent() {
    return dpkg -l "$1" &> /dev/null
}

if package_absent oracle-java8-installer ; then
    echo "deb http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | tee /etc/apt/sources.list.d/webupd8team-java.list
    echo "deb-src http://ppa.launchpad.net/webupd8team/java/ubuntu trusty main" | tee -a /etc/apt/sources.list.d/webupd8team-java.list

    # Accept license non-interactive
    echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys EEA14886
    apt-get update
    apt-get install -y oracle-java8-installer

    # Make sure Java 8 becomes default java
    apt-get install -y oracle-java8-set-default
fi

# Overall you don't need to install scala separately
if package_absent scala ; then
    wget http://www.scala-lang.org/files/archive/scala-2.11.8.deb
    dpkg -i scala-2.11.8.deb
    apt-get -y update
    apt-get -y install scala
fi

if package_absent sbt ; then
    wget http://dl.bintray.com/sbt/debian/sbt-0.13.11.deb
    dpkg -i sbt-0.13.11.deb
    apt-get -y update
    apt-get -y install sbt
fi

if package_absent mysql-server ; then
    apt-get install mysql-server
    mysql_secure_installation
    mysql_install_db
fi

if ! mysql -u root -e "use $DB_NAME"; then
    mysql -u root -e "create database $DB_NAME; GRANT ALL PRIVILEGES ON $DB_NAME.* TO $DB_USER_NAME@localhost IDENTIFIED BY '$DB_USER_PASSW'"
fi

cd /vagrant
sbt -v clean packageDebianUpstart

if ! package_absent wlxjury ; then
    dpkg -r wlxjury
fi

dpkg -i package/wlxjury-upstart-0.8.deb