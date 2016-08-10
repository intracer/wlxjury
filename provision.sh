#!/bin/bash

DB_NAME=wlxjury_db
DB_USER_NAME=wlx_jury_user
DB_USER_PASSW=wlx_jury

debianPackage=false

dpkg -l "oracle-java8-installer" &> /dev/null
if [ $? != 0 ] ; then
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

dpkg -l "sbt" &> /dev/null
if [ $? != 0 ] ; then
    wget http://dl.bintray.com/sbt/debian/sbt-0.13.11.deb
    dpkg -i sbt-0.13.11.deb
fi

dpkg -l "mysql-server" &> /dev/null
if [ $? != 0 ] ; then
    apt-get install -y mysql-server
    mysql_secure_installation
    mysql_install_db
fi

if ! mysql -u root -e "use $DB_NAME"; then
    mysql -u root -e "create database $DB_NAME; GRANT ALL PRIVILEGES ON $DB_NAME.* TO $DB_USER_NAME@localhost IDENTIFIED BY '$DB_USER_PASSW'"
fi

cd /vagrant

if [ "$debianPackage" = true ] ; then
    sbt -v clean packageDebianUpstart

    dpkg -l "wlxjury" &> /dev/null
    if [ $? == 0 ] ; then
        dpkg -r wlxjury
    fi

    dpkg -i package/wlxjury-upstart-0.8.deb
else
    sbt run
fi