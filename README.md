# E P O C H

The task scheduler on Drove

## Description

Epoch is task runner that allows you to run stand-alone Tasks on DROVE <br>
Think of it being very similar to Chronos, but on Drove and not Mesos.

## Features

- Self-serve UI for creating and managing tasks
- Ability to create a topology and schedule a recurring task
- Ability to run the above topology instantly

## Internals

### Terminology

_TASK:_ The primary unit of execution in Epoch is a Task. A task is a stand-alone unit of execution that run on
Drove. <br>
_TOPOLOGY:_  A topology is a definition of how the task is run - it can be scheduled to run at a particular time or at a
recurring intervals. <br>
- _Instant Run:_ A topology can be run instantly at a particular time
- _Scheduled Run:_ Every topology is created with a QUARTZ cron expression. This expression determines the schedule of
  the topology. <br>

> :warning: **Tasks On Drove**: Today, Drove only supports running tasks that are packaged as docker images. So, the
> task that you want to run on Drove should be packaged as a docker image and pushed to a Docker registry.

> :information_source: **Quartz Cron**: Epoch uses Quartz cron expressions to schedule tasks. You can read more about
> Quartz cron expressions [here](http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html)

### Architecture

The first important aspect is to understand Leader Election and how requests are routed across different epoch
instances. <br>
A single Epoch node is the leader to all requests from the UI, and is responsible for running all Topologies/Tasks, and doing the
various state management. <br>
The remaining nodes route requests to the leader node. <br>
This is done using a simple Zookeeper based leader election, and a Routing Servlet Filter. <br>

##### Why did we choose to do all of this on a single leader node, isn't it less scalable ?

The actual execution of the task is done on Drove. Epoch is only responsible for managing the state of the task, and the
topology. <br>
So the resource intensive operation (of actually running the docker task) is outside of Epoch. Since only tracking these
tasks through a scheduler is the only thing that Epoch does, one node of epoch should scale for 10s of thousands of task
runs.
Should we need to scale this further, we can always distribute the tasks across multiple nodes. <br>
But until then, with the choice of not distributing, tracking all of it through a single instance is more scalable. <br>
Internally, Epoch uses [Kaal](https://github.com/appform-io/kaal) for scheduling the status checks and the topology runs in memory. <br>  

A TLDR version of the request routing is represented below <br>

<img src="resources/leadershipSetup.png" width="80%">

#### Understanding the various states

As explained earlier, a task is a stand-alone unit of execution. <br>
The following diagram shows the various states of a task. This is inline with the states of a task in Drove.<br>

<img src="resources/taskRunStates.png" width="60%">

The state of the task determines the state of a topology run.<br>
The following diagram shows the various states of a specific run of the Topology <br>

<img src="resources/topologyRunStates.png" width="50%">


And finally, the above is only applicable if the Topology is not PAUSED. This is purely determined by the state of the
Topology, set using the UI<br>
The following shows the various states of a topology <br>

<img src="resources/topologyStates.png" width="40%">

#### What does a full create flow look like?
<img src="resources/createFlowDiagram.png" width="80%">

#### Zookeeper for storing tasks, runs and topologies

Epoch uses Zookeeper to store the tasks and topologies. The following diagram shows the structure of the data in
Zookeeper
<img src="resources/zkDataStructure.png" width="80%">

## Usage

The Epoch server container is available at [ghcr.io](ghcr.io/phonepe/epoch-server).

The container is intended to be run on a Drove cluster.

### Environment Variables
The following environment variables are understood by the container:

| Variable Name         |                              Required without external config                               | Description                                                                                                        |
|-----------------------|:-------------------------------------------------------------------------------------------:|--------------------------------------------------------------------------------------------------------------------|
| ZK_CONNECTION_STRING  |                        Yes. Unnecessary if config is being injected.                        | Connection String for the Zookeeper Cluster                                                                        |
| DROVE_ENDPOINT        |                        Yes. Unnecessary if config is being injected.                        | HTTP(S) endpoint for the Drove cluster                                                                             |
| DROVE_APP_NAME        |                                     Injected by  Drove                                      | App name for the container on the Drove cluster.<br> Do not keep changing this as it will lose stored job context. |
| CONFIG_FILE_PATH      | To use custom config file. Can be put on some executor and volume mounted in the container. | By default config file in `/home/default/config.yml` is used.                                                      |
| ADMIN_PASSWORD        |              **Optional but Recommended**. Unnecessary if config is injected.               | Password for the user `admin` which has read/write permissions. Default value is `admin`.                          |
| GUEST_PASSWORD        |              **Optional but Recommended**. Unnecessary if config is injected.               | Password for the user `guest` which has read only permissions. Default value is `guest`.                           |
| GC_ALGO               |                                          Optional                                           | GC to be used by JVM. By default G1GC is used.                                                                     |
| JAVA_PROCESS_MIN_HEAP |                                          Optional                                           | Minimum Java Heap size. Set to 1 GB by default.                                                                    |
| JAVA_PROCESS_MAX_HEAP |                                          Optional                                           | Maximum Java Heap size. Set to 1 GB by default.                                                                    |
| JAVA_OPTS             |                                          Optional                                           | Additional java options.                                                                                           |
| DEBUG                 |                                          Optional                                           | Set to a non-zero value to print environment variables etc. Note: This will print _all_ env variables.             |

### Deploying Epoch on Drove

The following is a sample app specification for deploying the epoch container on drove.

```json
{
  "name": "epoch",
  "version": "1",
  "executable": {
    "type": "DOCKER",
    "url": "ghcr.io/phonepe/epoch:latest",
    "dockerPullTimeout": "2 minute"
  },
  "exposedPorts": [
    {
      "name": "main",
      "port": 8080,
      "type": "HTTP"
    },
    {
      "name": "admin",
      "port": 8081,
      "type": "HTTP"
    }
  ],
  "volumes": [],
  "type": "SERVICE",
  "logging": {
    "type": "LOCAL",
    "maxSize": "10m",
    "maxFiles": 3,
    "compress": true
  },
  "resources": [
    {
      "type": "MEMORY",
      "sizeInMB": 4096
    },
    {
      "type": "CPU",
      "count": 1
    }
  ],
  "placementPolicy": {
    "type": "ANY"
  },
  "healthcheck": {
    "mode": {
      "type": "HTTP",
      "protocol": "HTTP",
      "portName": "admin",
      "path": "/healthcheck",
      "verb": "GET",
      "successCodes": [
        200
      ],
      "payload": "",
      "connectionTimeout": "20 seconds"
    },
    "timeout": "5 seconds",
    "interval": "20 seconds",
    "attempts": 3,
    "initialDelay": "0 seconds"
  },
  "readiness": {
    "mode": {
      "type": "HTTP",
      "protocol": "HTTP",
      "portName": "admin",
      "path": "/healthcheck",
      "verb": "GET",
      "successCodes": [
        200
      ],
      "payload": "",
      "connectionTimeout": "3 seconds"
    },
    "timeout": "3 seconds",
    "interval": "10 seconds",
    "attempts": 3,
    "initialDelay": "10 seconds"
  },
  "tags": {},
  "env": {
    "JAVA_PROCESS_MIN_HEAP": "2g",
    "JAVA_PROCESS_MAX_HEAP": "2g",
    "ADMIN_PASSWORD" : "adminpassword",
    "GUEST_PASSWORD" : "guestpassword",
    "ZK_CONNECTION_STRING" : "<YOUR_ZK_CONNECTION_STRING>",
    "DROVE_ENDPOINT" : "<YOUR_DROVE_ENDPOINT>"
  },
  "exposureSpec": {
    "vhost": "epoch.<YOUR_DOMAIN>",
    "portName": "main",
    "mode": "ALL"
  },
  "preShutdown": {
    "hooks": [],
    "waitBeforeKill": "30 seconds"
  },
  "instances" : 1
}
```

Replace the following in the above:
 - **YOUR_ZK_CONNECTION_STRING** - Connection string for your zookeeper cluster
 - **YOUR_DROVE_ENDPOINT** - HTTP(S) endpoint for Drove
 - **YOUR_DOMAIN** - Domain on which you want drove-nixy to configure nginx vhost

Save the json in some file say `epoch.json`

Deploy to drove using the following command:

```shell
drove -c testing apps create epoch.json
```
This will create an app called `epoch-1`.

Scale the app to one instance.
```shell
drove -c oss apps deploy epoch-1 1 -w
```

The drove app should be available at http://epoch.<YOU_DOMAIN>.

## License
Apache 2