#!/bin/bash

MYSQL_ROOT_PASSW=mysql_root_passw
WLXJURY_DB=wlxjury
WLXJURY_DB_USER=wlx_jury_user
WLXJURY_DB_PASSWORD=wlx_jury_passw

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

    debconf-set-selections <<< "mysql-server mysql-server/root_password password $MYSQL_ROOT_PASSW"
    debconf-set-selections <<< "mysql-server mysql-server/root_password_again password $MYSQL_ROOT_PASSW"

    apt-get install -y mysql-server
fi

mysql -u root -p$MYSQL_ROOT_PASSW -e "use $WLXJURY_DB" &> /dev/null
if [ $? != 0 ] ; then
    echo Creating database $WLXJURY_DB

    mysql -u root -p$MYSQL_ROOT_PASSW -e "create database $WLXJURY_DB; GRANT ALL PRIVILEGES ON $WLXJURY_DB.* TO $WLXJURY_DB_USER@localhost IDENTIFIED BY '$WLXJURY_DB_PASSWORD'"

    su vagrant -c "echo export WLXJURY_DB_USER=$WLXJURY_DB_USER >>~/.bash_profile"
    su vagrant -c "echo export WLXJURY_DB_PASSWORD=$WLXJURY_DB_PASSWORD >>~/.bash_profile"
fi

cd /vagrant

if [ "$debianPackage" = true ] ; then
    su vagrant -c "sbt -v clean packageDebianUpstart"

    dpkg -l "wlxjury" &> /dev/null
    if [ $? == 0 ] ; then
        dpkg -r wlxjury
    fi

    dpkg -i package/wlxjury-upstart-0.8.deb
else
    su vagrant -c "sbt compile"
fi