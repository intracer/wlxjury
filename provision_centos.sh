#!/bin/bash

DB_NAME=wlxjury_db
DB_USER_NAME=wlx_jury_user
DB_USER_PASSW=wlx_jury

rpm --quiet -q java-1.8.0-openjdk
if [ $? != 0 ]  ; then
    yum -y install java-1.8.0-openjdk*
fi

rpm --quiet -q sbt
if [ $? != 0 ]  ; then
    curl https://bintray.com/sbt/rpm/rpm > bintray-sbt-rpm.repo
    mv bintray-sbt-rpm.repo /etc/yum.repos.d/
    yum -y install sbt
fi

rpm --quiet -q mariadb-server
if [ $? != 0 ]  ; then
    yum -y install mariadb-server
    systemctl start mariadb
    systemctl enable mariadb
fi

if ! mysql -u root -e "use $DB_NAME"; then
    mysql -u root -e "create database $DB_NAME; GRANT ALL PRIVILEGES ON $DB_NAME.* TO $DB_USER_NAME@localhost IDENTIFIED BY '$DB_USER_PASSW'"
fi

cd /vagrant
sbt -v clean packageRpmSystemd

rpm --quiet -q wlxjury
if [ $? = 0 ]  ; then
    rpm -e wlxjury
fi

rpm -i package/wlxjury-systemd-0.8.rpm