# E P O C H
The task scheduler on Drove

## Description
Epoch is task runner that allows you to run stand-alone Tasks on DROVE <br>
Think of it being very similar to Chronos, but on Drove and not Mesos.

## Features
- Authentication and Authorization using Olympus
- Self-serve UI for creating and managing tasks
- Schedule a one time task
- Schedule a recurring task

## Internals

### Terminology
_TASK:_ The primary unit of execution in Epoch is a Task. A task is a stand-alone unit of execution that run on Drove. <br>
_TOPOLOGY:_  A topology is a definition of how the task is run - it can be scheduled to run at a particular time or at a recurring intervals. <br>
 - _Instant Run:_ A topology can be run instantly at a particular time
 - _Scheduled Run:_ Every topology is created with a QUARTZ cron expression. This expression determines the schedule of the topology. <br>

> :warning: **Tasks On Drove**: Today, Drove only supports running tasks that are packaged as docker images. So, the task that you want to run on Drove should be packaged as a docker image and pushed to the Docker registry on PhonePe. <br>
> :information_source: **Quartz Cron**: Epoch uses Quartz cron expressions to schedule tasks. You can read more about Quartz cron expressions [here](http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html)

### Understanding the various states

As explained earlier, a task is a stand-alone unit of execution. <br> 
The following diagram shows the various states of a task. This is inline with the states of a task in Drove.<br>
<img src="resources/taskRunStates.png" width="70%">

The state of the task determines the state of a topology run.<br>
The following diagram shows the various states of a specific run of the Topology <br>
<img src="resources/topologyRunStates.png" width="50%">

And finally, the above is only applicable if the Topology is not PAUSED. This is purely determined by the state of the Topology, set using the UI<br>
The following shows the various states of a topology <br>
<img src="resources/topologyStates.png" width="40%">


### Zookeeper for storing tasks

Epoch uses Zookeeper to store the tasks and topologies. The following diagram shows the structure of the data in Zookeeper
<img src="resources/zkDataStructure.png" width="60%">

