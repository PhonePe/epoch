#!/usr/bin/env bash
export MAVEN_OPTS="-Xms1024m -Xmx6096m -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
if [[ "$1" = 'd' ]]
then cd .. && mvn clean install -DskipTests -Plocal -pl !epoch-server && cd epoch-server
fi
export TEAM_ID=infra
export DROVE_APP_NAME=epoch
mvn compile -Plocal exec:java -Dexec.mainClass="com.phonepe.epoch.server.App" -Duser.timezone=IST -DlocalConfig=true -Dexec.args="server configs/local.yml"
#mvn clean install -DskipTests -Plocal && java -jar -XX:+UseG1GC -Xms1g -Xmx1g -DlocalConfig=true -Ddb.shards=1 target/epoch-server-1.0-SNAPSHOT.jar server config/local.yml