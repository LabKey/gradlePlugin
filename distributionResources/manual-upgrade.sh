#!/bin/sh
#
# Copyright (c) 2006-2018 LabKey Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
###############################################################################################
# LabKey Server Upgrade Script.
# This script can be used to upgrade an installed LabKey Server instance running
# on Linux, MacOSX or Solaris. The script assumes that you have installed the LabKey Server
# following the instructions at
# https://www.labkey.org/Documentation/wiki-page.view?name=manualInstall.
#
# If you have installed the LabKey Server using the Graphical Windows Installer, please use the
# latest version of Graphical Windows Installer to upgrade the LabKey Server
#
# If you have installed the LabKey Server using the Linux Installer (install-labkey.py), please
# use the Linux Installer upgrade script, upgrade-labkey.py, to upgrade the LabKey Server
#
# It is recommended that you perform an backup of your database and files before using this 
# script to upgrade your server.
#
# Important Documentation
#  - Documentation for using this script:
#    https://www.labkey.org/Documentation/wiki-page.view?name=manualUpgradeScript
#  - Documentation for performing a manual upgrade (ie not using this script):
#    https://www.labkey.org/Documentation/wiki-page.view?name=manupgrade
#  - LabKey Upgrade Support Policy:
#    https://www.labkey.org/Documentation/wiki-page.view?name=upgradeSupportPolicy
#
#################################################################################################


print_usage()
{
    echo "Usage:"
    echo "    manual-upgrade.sh -l dir [-d dir] [-c dir] [-u tomcatuser] [--noPrompt] [--service|--systemctl|--catalina|--tomcat_lk|--skip_tomcat]"
    echo ""
    echo "    -l dir: LABKEY_HOME directory to be upgraded. This directory contains the "
    echo "            the labkeywebapp, modules, pipeline-lib, etc directories for the existing "
    echo "            LabKey Server instance. (Required)"
    echo ""
    echo "    -d dir: Upgrade distribution directory: contains labkeywebapp, lib, and this script."
    echo "            (default: `pwd`)"
    echo ""
    echo "    -c dir: TOMCAT_HOME; root of LabKey Apache Tomcat installation."
    echo "            (default: $CATALINA_HOME)"
    echo ""
    echo "    -u owner: the tomcat user account (default: `whoami`)"
    echo ""
    echo "    --noPrompt: do not require the user to hit enter before proceeding with the install"
    echo ""
    echo "    (Web server startup/shutdown method: select one of the following)"
    echo ""
    echo "        --service: use /etc/init.d/tomcat (default)"
    echo ""
    echo "        --systemctl: use /bin/systemctl"
    echo ""
    echo "        --catalina: use TOMCAT_HOME/bin/shutdown.sh and TOMCAT_HOME/bin/startup.sh"
    echo ""
    echo "        --tomcat_lk: for LabKey Internal Hosted Server Installed Only"
    echo ""
    echo "        --skip_tomcat: the script will not attempt to start/stop tomcat.  You must do this manually before/after running the script."
    echo ""
}

print_error()
{
    echo ""
    echo " ERROR: The upgrade did not complete successfully. See the error message"
    echo " above for more information "
    echo ""
    exit 1

}

#
# Variables
#
service="true"
systemctl="false"
catalina="false"
tomcat_lk="false"
skip_tomcat="false"
noPrompt="false"
labkey_home=""
tomcat_home=$CATALINA_HOME
labkey_binary_distro_dir=.
tomcat_user="-"
service_script="/etc/init.d/tomcat"
DATE=$(date +%Y-%m-%d\ %H:%M)
count=0

#
# Parse command line arguments
#
if [ -z "$1" ]
then
    print_usage
    exit
fi
while [ -n "$1" ]
do
  case $1
  in
    -c)
    if [ -z $2 ] || [ "$(echo $2 | cut -c1)" = "-" ]
    then
        echo "Please specify a directory with the -c option. See the Usage text below"
        echo ""
        print_usage
        exit
    fi
    tomcat_home=$2;
    shift 2;;
    -l)
    if [ -z $2 ] || [ "$(echo $2 | cut -c1)" = "-" ]
    then
        echo "Please specify a directory with the -l option. See the Usage text below"
        echo ""
        print_usage
        exit
    fi
    labkey_home=$2;
    shift 2;;
    -d)
    if [ -z $2 ] || [ "$(echo $2 | cut -c1)" = "-" ]
    then
        echo "Please specify a directory with the -d option. See the Usage text below"
        echo ""
        print_usage
        exit
    fi
    labkey_binary_distro_dir=$2;
    shift 2;;
    -u)
    if [ -z $2 ] || [ "$(echo $2 | cut -c1)" = "-" ]
    then
        echo "Please specify the tomcat user with the -u option. See the Usage text below"
        echo ""
        print_usage
        exit
    fi
    tomcat_user=$2;
    shift 2;;
    --service)
    service="true";
    shift;;
    --systemctl)
    systemctl="true";
    service="false"
    shift;;
    --catalina)
    catalina="true";
    service="false"
    shift;;    
    --tomcat_lk)
    tomcat_lk="true";
    service="false"
    shift;;
    --skip_tomcat)
    skip_tomcat="true";
    service="false"
    shift;;
    --noPrompt)
    noPrompt="true";
    shift;;
    *)
    echo "Option [$1] not one of  [c, l, d, t, u, service, catalina, noPrompt]";
    print_usage
    exit;;
  esac
done


#
# More Variables
#
# strip trailing slashes off of paths for easier path contruction later:
labkey_home=`echo $labkey_home | sed 's;/$;;'`
tomcat_home=`echo $tomcat_home | sed 's;/$;;'`
labkey_binary_distro_dir=`echo $labkey_binary_distro_dir | sed 's;/$;;'`


#
# validate parameters
#
echo ""
echo ""
echo "-------------  Starting the Upgrade at " `date`
echo ""

echo "Verifying installation directories and upgrade files..."

if [ -z $labkey_home ] || [ ! -d $labkey_home ]
then
  echo ""
  echo "LABKEY_HOME location, $labkey_home, is either invalid or not provided.  Please specify correct path on the command line."
  echo ""
  print_usage
  exit 1
fi

if [ -z $labkey_binary_distro_dir ] || [ ! -d $labkey_binary_distro_dir ] || [ ! -d "$labkey_binary_distro_dir/labkeywebapp" ]
then
  echo ""
  echo "The LabKey distribution location, $labkey_binary_distro_dir, was either invalid or not provided.  Please specify correct path on the command line."
  echo ""
  print_usage
  exit 1
fi

if [ -z $tomcat_home ] || [ ! -d $tomcat_home ]
then
  echo ""
  echo "TOMCAT_HOME location, $tomcat_home, is either invalid or not provided."
  echo "Please specify the correct path on the command line, or set the CATALINA_HOME environment variable."
  echo ""
  print_usage
  exit 1
fi

CATALINA_HOME=$tomcat_home
# export CATALINA_HOME

echo ""
echo " Installation options have been verified"

#
# Ask the administrator to backup the database.
#

echo ""
echo " LabKey recommends performing a database backup before moving forward with the upgrade."
echo "  - See https://www.labkey.org/Documentation/wiki-page.view?name=backupScenarios "
echo "    for more information on how to perform a backup of your database."
echo ""
echo " Additionally, the upgrade process will replace the current contents of your deployment's"
echo " ./modules and ./labkeyWebapp directories. If you have added your own modules or content, "
echo " please migrate to using ./externalModules before proceeding."
echo "  - See https://www.labkey.org/Documentation/wiki-page.view?name=moduleUpgrades"
echo ""

if [ "$noPrompt" = "false" ]
then
    echo " Press [Enter] to start the upgrade. "
    read -p " " read_variable
fi

#
# Stop the Tomcat server
#

echo " Stopping the LabKey/Tomcat server..."
if [ "$service" = "true" ]
then
    sudo $service_script stop
elif [ "$systemctl" = "true" ]
then
    sudo /bin/systemctl stop tomcat.service
elif [ "$tomcat_lk" = "true" ]
then
    sudo service tomcat_lk stop
elif [ "$catalina" = "true" ]
then
    $tomcat_home/bin/shutdown.sh
elif [ "$skip_tomcat" = "true" ]
then
    echo "skip_tomcat=true, so tomcat will not be shutdown"
fi

#
# Check if Tomcat has successfully shutdown
#
PID=$(ps ax | grep bootstrap.jar | grep catalina)
if [ -n "$PID" ]
then
    /bin/echo -n " Waiting for the Tomcat to shutdown..."
    sleep 10
    while [ ${count} -le 15 ]
    do
        PID=$(ps ax | grep bootstrap.jar | grep catalina)
        if [ -n "$PID" ]
        then
            /bin/echo -n "."
            sleep 2
            count=`expr $count + 1`
        else
            echo ""
            break
        fi
    done
fi

if [ "$count" = "16" ]
then
    echo ""
    echo " The LabKey/Tomcat server did not respond to the stop request and is "
    echo " still running. The upgrade will not proceed. "
    echo ""
    echo " Please see the Tomcat logs, in the directory $tomcat_home/logs, "
    echo " for more information."
    echo ""
    exit
fi

#
# Remove LabKey installed files in LABKEY_HOME
#
echo ""
echo " Remove the currently installed LabKey binaries from the $labkey_home directory"
rm -rf $labkey_home/modules
if [ $? != 0 ]; then print_error; fi # exit if the last command failed
rm -rf $labkey_home/labkeywebapp
if [ $? != 0 ]; then print_error; fi # exit if the last command failed
if [ -x $labkey_home/pipeline-lib ]; then
  rm -rf $labkey_home/pipeline-lib
  if [ $? != 0 ]; then print_error; fi # exit if the last command failed
fi

#
# Install the new version of LabKey in LABKEY_HOME
#
echo ""
echo " Install the new version of LabKey into $labkey_home directory "
cd $labkey_binary_distro_dir

#
# As of 16.2.1 sprint build, /bin directory no longer exists in the Linux/OSX distributions, #26103 & #26210
#
# cp -f bin/* $labkey_home/bin
# if [ $? != 0 ]; then print_error; fi # exit if the last command failed

cp -R modules $labkey_home
if [ $? != 0 ]; then print_error; fi # exit if the last command failed
cp -R labkeywebapp $labkey_home
if [ $? != 0 ]; then print_error; fi # exit if the last command failed
if [ -x pipeline-lib ]; then
  cp -R pipeline-lib $labkey_home
  if [ $? != 0 ]; then print_error; fi # exit if the last command failed
fi

#
# Remove all existing LabKey JAR files from TOMCAT_HOME/lib directory, if found
# Throws warning if the forced delete fails.
#
removeTomcatJar()
{
  jar=$1
  file=$CATALINA_HOME/lib/$jar
  if [ -f $file ]
  then
      echo "Deleting the $file file..."
      rm -f $file;
      retValue=$?
      if [ $retValue -ne 0 ]; then
          echo "FAILED"
          deleteFailed=1
      fi
  fi
}

deleteFailed=0
removeTomcatJar 'javax.activation.jar'
removeTomcatJar 'labkeyBootstrap.jar'
removeTomcatJar 'mysql.jar'
removeTomcatJar 'jtds.jar'
removeTomcatJar 'mail.jar'
removeTomcatJar 'postgresql.jar'

if [ $deleteFailed -eq 1 ]; then
    echo "Unable to delete one or more legacy jar files from $CATALINA_HOME/lib.  You will need to remove these manually in order for the server to start cleanly."
fi

#
# Copy the LabKey jar files (libraries) to the TOMCAT_HOME directory
#
echo ""
echo " Install the LabKey jar files(libraries) into $tomcat_home/lib directory "
cp -f tomcat-lib/*.jar $CATALINA_HOME/lib
if [ $? != 0 ]; then print_error; fi # exit if the last command failed

#
# Ensure that the user running the tomcat server owns all the files
#
echo ""
echo " Setting owner for the newly installed files"
if [ $tomcat_user != "-" ]; then
    chown -R $tomcat_user $labkey_home
    chown -R $tomcat_user $tomcat_home/lib
fi

#
# Start the LabKey/Tomcat Server
#
echo ""
echo " Starting Apache Tomcat..."
if [ $service = "true" ]
then
    sudo $service_script start
elif [ $systemctl = "true" ]
then
    sudo /bin/systemctl start tomcat.service
elif [ $tomcat_lk = "true" ]
then
    sudo service tomcat_lk start
elif [ $catalina = "true" ]
then
    $tomcat_home/bin/startup.sh
elif [ "$skip_tomcat" = "true" ]
then
    echo "skip_tomcat=true, so tomcat will not be restarted"    
fi

#
# Write out upgrade log.
# The upgrade log is a record of all LabKey Server
# upgrades which occur on this server. The log will collect
# the date of the upgrade, LabKey Server version and other
# important information.
#
installLog=$labkey_home/labkey_installs.txt
hostname=`hostname -f`
if [ -z "$SUDO_USER" ]
then
    user=$USER
else
    user=$SUDO_USER
fi
distname=`basename $labkey_binary_distro_dir`
echo $DATE, $hostname, $user, $distname >> $installLog


echo ""
echo " The upgrade has completed successfully "
echo ""
echo " If the LabKey server does not start properly see the log files at "
echo "  - Tomcat startup log: $tomcat_home/logs/catalina.out "
echo "  - LabKey specific log: $tomcat_home/logs/labkey.log "
echo ""
echo " ------------- The upgrade has completed at "  `date`
echo ""
