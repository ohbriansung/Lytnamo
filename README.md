# Lytnamo

## Introduction

Lytnamo is a lite implementation of Amazon's Dynamo [\[1\]](#references). Most of the concepts are inspired by the paper issued by Amazon referenced in the end of this document. Lytnamo is a Key-value Distributed Storage System. The system contains with one membership coordinator to maintain membership and assign keys to replicas, one frontend as interface to receive requests from client, and several synchronized divergent backend replicas to store and replicate data.

## Architecture

![Lytnamo architecture](https://i.imgur.com/hboZtow.jpg)

## Features

### Quorum-like System

To maintain consistency among its replicas, Lytnamo uses a consistency protocol similar to those used in quorum systems by configuring three parameters N, W, and R when the server starts. Each data with particular key is replicated into N backend replicas, and the N bockend replicas is the *Preference List* of that key. W is the minimum number of nodes that must participate in a successful write operation. R is the minimum number of nodes that must participate in a successful read operation.

### Membership and Failure Detection

#### Gossip

A gossip-based protocol propagates membership changes and maintains an eventually consistent view of membership. A backend replica will keep gossiping with another backend replica randomly once every second. During the gossip, backend replicas will exchange their node adding/removing log, and they will use the log to reconcile their membership. Frontend will also gossip with backend replicas, but it will only receive the log from backend, and will not offer its log to backend replicas.

#### External Discovery

The paper mentioned a technic to avoid logically partitioned. For example, two nodes A and B join into the ring, but they cannot discover each other. Base on the brief description in the paper, I made some assumptions and implemented a mechanism to achieve that technic. There are two types of backend replica in Lytnamo which is either a seed or not. A seed is known to all nodes. Every time a new replica comes up, it registers itself to *Membership Coordinator*, then the coordiator assigns a key to the new replica, and adds the new node into the ring. Then, the coordinator responses to the new replica with a list of all seed nodes. So the new replica can start gossip with seed nodes, seed nodes can add the new node into the membership, and eventually, all nodes can know there is a new replica, which is fairly fast.

#### Failure Detection

Backend replicas detect failure of other nodes during gossip and read/write operations. There are two types of failure: temporary failure and permanent failure. We will discuss the details later in [Failure Handling](#failure-handling) section.

### Consistent Hashing

Every read/write request contains a key parameter indicated in the uri. The frontend assigns the request to a backend replica by hashing the key to yield its position on the ring, and then walking the ring clockwise to find the first node with a position larger than the key's position.

#### Load Balancing

Instead of sending the request to the first node described above, frontend randomly picks one of the node in the preference list to send the request, and the node that is responsible for the request is called *Replication Coordinator*.

### Read/Write Operation and Replication

Frontend sends a read/write request to a backend replica, which is the replication coordinator described above. After it receives the request from a client, the replication coordinator checks if itself is in the preference list of the key in the request. If it is not, it redirects frontend to send the request to the correct replica. If it is, then it stores the data into its data storage, and start the replication process to other replicas in the preference list. The replication coordinator will response to the fronend base on the configuration of W and R. That is, it will response after W replicas, including the coordinator itself, successfully store the data. The rest of the replication operations will continue asynchronously. Similarly, for read request, the coordinator requests and gathers data from all replicas in the preference list. If the coordinator ends up gathering multiple versions of the data, it returns all the versions it deems to be causally unrelated. The divergent versions can be reconciled by the client later. In addition, if we set the W or R value equals to N, then the system will be fully synchronous.

Lytnamo provides two write operations: add item and remove item.

### Data Versioning

![Lytnamo data versioning](https://i.imgur.com/Jbt4IeN.jpg)

Lytnamo provides enventual consistency, which allows for updates to be propagated to all replicas asynchronously. In order to capture causality between different versions of the same object, Lytnamo uses Vector Clock, whcih is effectively a list of (node, timestamp) pairs. Each object, which is the data of particular key, in the data storage holds a Vector Clock. A replication coordinator updates the Vector Clock of an object by increasing the timestamp of its pair. And it then passes the object with the Vector Clock to other replicas. For each write operation, the client needs to indicate the version it is updating. If it is updating an older version, the coordinator will start a read operation and return the latest version, so the client can update to with the latest version later. For read operation, the client will always read from the latest version.

### Adding/Removing Storage Nodes

![Lytnamo adding/removing nodes](https://i.imgur.com/9VSSO0i.jpg)

First, the new replica X registers itself to Membership Coordinator, and the coordinator automatically calculates the key (slot) on the ring for this new replica by finding the middle point of the largest space between other replica pairs. For example, space between A and B is the largest, then, X will be assign to the middle of that space.

When a new replica X is added into the ring between A and B. Due to the allocation of key ranges to X, some existing nodes (X's N successors) no longer have to handle some of their keys and these nodes transfer data base on those keys to X and remove those data from their end. For this particular example: B transfers data with keys between (E, F], C transfers data with keys between (F, A], and D transfers data with keys between (A, X]. When a node is removed from the system, the reallocation of keys happens in a reverse process. Predecessors of B, C, and D will offer data within particular key range, but this reallocate operation will not remove data from sender. The notification of transfer is been initialized by the membership coordinator when a new node registers to it.

### Failure Handling

#### Temporary Failure: Hinted Handoff

![Lytnamo hinted handoff](https://i.imgur.com/h3bIK3w.jpg)

Temporary failure is dicovered during read/write operation. When a replica is temporary unreachable, the replication coordinator will add hinted information, and send the hinted data to the preference list's next replica (N + 1th node). When a replica receives the hinted data, it stores the data. The replica that received the hinted data will send the data along with the next gossip to the replica that supposes to store this data. The hinted data will then be sent to a correct replica after the partition of the ring. If the gossip proceed successfully, the node that holds hinted data previously will remove it from its end.

#### Permanent Failure

Permanent failure is discovered during gossip operation. Lytnamo treats permanent failure as removing a node from the ring, handles as the operation described in the [Removing Storage Nodes](#readwrite-operation-and-replication) section.

### Reconcile: Merge

When a client receives multi versions of an object after read request, the client can indicate the version(s) it want to reconcile. Then, the replication coordinator will merge the items in the object and recalculate the vector clock, and pass the reconciled version to other replicas. For example:<br/>
Two versions of data:<br/>
<pre>
[{"items":["cs682", "cs631"], "clocks": [{"node": "n1", "timestamp": 2}]},
{"items":["cs682", "cs601"], "clocks": [{"node": "n1", "timestamp": 1}, {"node": "n2", "timestamp": 1}]}]
</pre>
After reconciliation by node n1:<br/>
<pre>
{"items":["cs682", "cs631", "cs601"], "clocks": [{"node": "n1", "timestamp": 3}, {"node": "n2", "timestamp": 1}]}
</pre>
(n1 calculates the maximum of each clock and increases its clock since it is the replication coordinator)

## APIs

### Membership Coordinator

<details>
<summary>POST /register</summary>

Request body:

<pre>
{
    "id": "node_uuid",
    "host": "host_address",
    "port": "listening_port",
    "seed": false,
    "key": -1
}
</pre>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Registration success<br/>
<pre>
{
    "key": 190,
    "capacity": 256,
    "N": 3,
    "W": 2,
    "R": 2,
    "seeds": [
        {
            "id": "seed_uuid",
            "host": "seed_address",
            "port": "seed_listening_port",
            "seed": true,
            "key": 0
        },
        {
            "id": "seed_uuid",
            "host": "seed_address",
            "port": "seed_listening_port",
            "seed": true,
            "key": 127
        }
    ]
}
</pre>
    </tr>
    <tr><td>400</td><td>Unable to register node into the ring</tr>
</table>
</details>

<details>
<summary>POST /deregister</summary>

Request body:

<pre>
{
    "id": "node_uuid",
    "host": "host_address",
    "port": "listening_port",
    "seed": false,
    "key": 190
}
</pre>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Deregistration success</tr>
</table>
</details>

<details>
<summary>GET /seeds</summary>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Registration success<br/>
<pre>
{
    "capacity": 256,
    "N": 3,
    "W": 2,
    "R": 2,
    "seeds": [
        {
            "id": "seed_uuid",
            "host": "seed_address",
            "port": "seed_listening_port",
            "seed": true,
            "key": 0
        },
        {
            "id": "seed_uuid",
            "host": "seed_address",
            "port": "seed_listening_port",
            "seed": true,
            "key": 127
        }
    ]
}
</pre>
    </tr>
</table>
</details>

### Backend Replica

<details>
<summary>GET /gossip</summary>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Add/Delete log and current replicas<br/>
<pre>
{
    "add": ["node1_uuid", "node2_uuid", "node3_uuid"],
    "delete": ["node3_uuid"],
    "replicas": {
        "node1_uuid":
        {
            "id": "node1_uuid",
            "host": "node1_address",
            "port": "node1_listening_port",
            "seed": true,
            "key": 0
        },
        "node2_uuid":
        {
            "id": "node2_uuid",
            "host": "node2_address",
            "port": "node2_listening_port",
            "seed": true,
            "key": 127
        }
    }
}
</pre>
    </tr>
</table>
</details>

<details>
<summary>POST /gossip</summary>

Request body:

<pre>
{
    "add": ["node1_uuid", "node2_uuid", "node3_uuid"],
    "delete": ["node3_uuid"],
    "replicas": {
        "node1_uuid":
        {
            "id": "node1_uuid",
            "host": "node1_address",
            "port": "node1_listening_port",
            "seed": true,
            "key": 0
        },
        "node2_uuid":
        {
            "id": "node2_uuid",
            "host": "node2_address",
            "port": "node2_listening_port",
            "seed": true,
            "key": 127
        }
    }
}
</pre>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Add/Delete log and current replicas<br/>
<pre>
{
    "add": ["node1_uuid", "node2_uuid"],
    "delete": [],
    "replicas": {
        "node1_uuid":
        {
            "id": "node1_uuid",
            "host": "node1_address",
            "port": "node1_listening_port",
            "seed": true,
            "key": 0
        },
        "node2_uuid":
        {
            "id": "node2_uuid",
            "host": "node2_address",
            "port": "node2_listening_port",
            "seed": true,
            "key": 127
        }
    }
}
</pre>
    </tr>
    <tr><td>400</td><td>Incorrect request body format: json</tr>
</table>
</details>

<details>
<summary>GET /get/{hashKey}/{key}</summary>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Object data<br/>
<pre>
[
    {
        "items": ["cs682","cs631"],
        "clocks": [
            {
                "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
                "timestamp": 1
            },
            {
                "node": "c41eafcf-046c-41d1-835f-f6ebcc2937ac",
                "timestamp": 1
            }
        ]
    },
    {
        "items": ["cs682","cs601"],
        "clocks": [
            {
                "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
                "timestamp": 2
            }
        ]
    }
]
</pre>
    </tr>
    <tr><td>307</td><td>Not responsible for this key, redirect to:<br/>
<pre>
{
    "address": "correct_node_address:port"
}
</pre>
    </tr>
    <tr><td>400</td><td>No data</tr>
</table>
</details>

<details>
<summary>POST /put/{hashKey}/{key}</summary>

Request body:

<pre>
{
    "op": "add",
    "item": "cs631",
    "version": [
        {
            "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
            "timestamp": 1
        }
    ]
}
</pre>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Object is stored successfully</tr>
    <tr><td>302</td><td>Version is too old, update with this version:<br/>
<pre>
[
    {
        "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
        "timestamp": 1
    },
    {
        "node": "c41eafcf-046c-41d1-835f-f6ebcc2937ac",
        "timestamp": 1
    }
]
</pre>
    </tr>
    <tr><td>307</td><td>Not responsible for this key, redirect to:<br/>
<pre>
{
    "address": "correct_node_address:port"
}
</pre>
    </tr>
</table>
</details>

<details>
<summary>GET /internal_get/{hashKey}/{key}</summary>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Object data<br/>
<pre>
{
    "items": ["cs682","cs601"],
    "clocks": [
        {
            "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
            "timestamp": 2
        }
    ]
}
</pre>
    </tr>
    <tr><td>307</td><td>Not responsible for this key, redirect to:<br/>
<pre>
{
    "address": "correct_node_address:port"
}
</pre>
    </tr>
    <tr><td>400</td><td>No data</tr>
</table>
</details>

<details>
<summary>POST /reconcile/merge/{hashKey}/{key}</summary>

Request body:

<pre>
[
    {
        "items": ["cs682","cs631"],
        "clocks": [
            {
                "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
                "timestamp": 1
            },
            {
                "node": "c41eafcf-046c-41d1-835f-f6ebcc2937ac",
                "timestamp": 1
            }
        ]
    },
    {
        "items": ["cs682","cs601"],
        "clocks": [
            {
                "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
                "timestamp": 2
            }
        ]
    }
]
</pre>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Reconciliation scuess</tr>
    <tr><td>307</td><td>Not responsible for this key, redirect to:<br/>
<pre>
{
    "address": "correct_node_address:port"
}
</pre>
    </tr>
</table>
</details>

<details>
<summary>POST /hinted/put</summary>

Request body:

<pre>
{
    "id": "c41eafcf-046c-41d1-835f-f6ebcc2937ac",
    "hashKey": 97,
    "key": "brian",
    "op": "add",
    "item": "cs631",
    "version": [
        {
            "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
            "timestamp": 1
        }
    ],
    "clocks": [
        {
            "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
            "timestamp": 1
        },
        {
            "node": "c41eafcf-046c-41d1-835f-f6ebcc2937ac",
            "timestamp": 1
        }
    ],
    "replicate": true
}
</pre>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Hinted data is stored successfully</tr>
</table>
</details>

<details>
<summary>POST /transfer</summary>

Request body:

<pre>
{
    "to": "node_address:port_copy_to",
    "from": "node_address:port_copy_from",
    "range": [0,255],
    "remove": false
}
</pre>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Transfer scuess</tr>
    <tr><td>400</td><td>Unable to transfer or incorrect request body format</tr>
</table>
</details>

<details>
<summary>POST /receiver</summary>

Request body:

<pre>
[
    {
        "hashKey": 6,
        "data": [
            {
                "key": "brian",
                "object": {
                    "items": ["cs682"],
                    "clocks": [
                        {
                            "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
                            "timestamp": 1
                        }
                    ],
                    "replicate": true
                }
            }
        ]
    },
    {
        "hashKey": 97,
        "data": [
            {
                "key": "a",
                "object": {
                    "items": ["testing","testing","testing","testing"],
                    "clocks": [
                        {
                            "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
                            "timestamp": 4
                        }
                    ],
                    "replicate": true
                }
            }
        ]
    }
]
</pre>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Data restore scuess</tr>
    <tr><td>400</td><td>Incorrect request body format</tr>
</table>
</details>

### Frontend

<details>
<summary>GET /get/{key}</summary>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Object data<br/>
<pre>
[
    {
        "items": ["cs682","cs631"],
        "clocks": [
            {
                "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
                "timestamp": 1
            },
            {
                "node": "c41eafcf-046c-41d1-835f-f6ebcc2937ac",
                "timestamp": 1
            }
        ]
    },
    {
        "items": ["cs682","cs601"],
        "clocks": [
            {
                "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
                "timestamp": 2
            }
        ]
    }
]
</pre>
    </tr>
    <tr><td>307</td><td>Not responsible for this key, redirect to:<br/>
<pre>
{
    "address": "correct_node_address:port"
}
</pre>
    </tr>
    <tr><td>400</td><td>No data</tr>
</table>
</details>

<details>
<summary>POST /put/{key}</summary>

Request body:

<pre>
{
    "op": "add",
    "item": "cs631",
    "version": [
        {
            "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
            "timestamp": 1
        }
    ]
}
</pre>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Object is stored successfully</tr>
    <tr><td>302</td><td>Version is too old, update with this version:<br/>
<pre>
[
    {
        "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
        "timestamp": 1
    },
    {
        "node": "c41eafcf-046c-41d1-835f-f6ebcc2937ac",
        "timestamp": 1
    }
]
</pre>
    </tr>
    <tr><td>307</td><td>Not responsible for this key, redirect to:<br/>
<pre>
{
    "address": "correct_node_address:port"
}
</pre>
    </tr>
    <tr><td>400</td><td>Write failed</tr>
</table>
</details>

<details>
<summary>POST /reconcile/merge/{key}</summary>

Request body:

<pre>
[
    {
        "items": ["cs682","cs631"],
        "clocks": [
            {
                "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
                "timestamp": 1
            },
            {
                "node": "c41eafcf-046c-41d1-835f-f6ebcc2937ac",
                "timestamp": 1
            }
        ]
    },
    {
        "items": ["cs682","cs601"],
        "clocks": [
            {
                "node": "070568e8-3c04-46ef-b5d9-eaadf972ce41",
                "timestamp": 2
            }
        ]
    }
]
</pre>

Responses:

<table>
    <tr><td>Code</td><td>Description</td></tr>
    <tr><td>200</td><td>Reconciliation scuess</tr>
    <tr><td>307</td><td>Not responsible for this key, redirect to:<br/>
<pre>
{
    "address": "correct_node_address:port"
}
</pre>
    </tr>
    <tr><td>400</td><td>Write failed</tr>
</table>
</details>

## Program and testing framework configuration

<details>
<summary>Start Membership Coordinator</summary>

```
$ java -jar Coordinator.jar -p <port> -max <max_ring_size> -n <nodes_in_preference_list> -w <min_nodes_write> -r <min_nodes_read>
```

</details>

<details>
<summary>Start Backend Replica</summary>

```
$ java -jar Backend.jar -p <port> -s <seed_or_not> -c <coordinator_address>
```

</details>

<details>
<summary>Start Frontend</summary>

```
$ java -jar Frontend.jar -p <port> -c <coordinator_address>
```

</details>

<details>
<summary>Write Test</summary>

```
$ python3 test_write.py <address> <key> <op> <item> <version>
```

</details>

<details>
<summary>Read Test</summary>

```
$ python3 test_read.py <address> <key>
```

</details>

<details>
<summary>Redirection Test</summary>

```
$ python3 test_redirect.py <address> <hashKey> <key>
```

</details>

<details>
<summary>Concurrent Write Test</summary>

```
$ java -jar ConcurrentTest.jar -t <target_address> -k <key> -d1 '<json_data_1>' -d2 '<json_data_2>'
```

</details>

<details>
<summary>Hinted Handoff Test</summary>

Just add a "demo" parameter in the write request body like this:
<pre>
{
    "op": "add",
    "item": "lytnamo",
    "version": [],
    "demo": true
}
</pre>

</details>

## Milestones

* Environment setup and create frontend/backend services with basic feature: configure with pass-in arguments when service starts, and put/get methods APIs.
* Implementing Membership with gossip-base protocol.
* Implementing Consistent Hashing to support adding backend services and maintain the services in the ring.
* Developing testing framework for features above.
* Checkpoint on Tuesday 5/8/18 to complete and demonstrate the implementations above.
* Implementing Failure Decetion and Handling (removing).
* Implementing Replication.
* Implementing Data Versioning.
* Developing final testing framework.
* Demonstration on Tuesday 5/15/18.
* Due Wednesday 5/16/18 5pm PDT.

## Author and Contributors

0. Chien-Yu (Brian) Sung
1. Dr. Rollins - [page](http://srollins.cs.usfca.edu/)

## Acknowledgment

This project is for academic purposes only.

## References
0. [University of San Francisco](https://www.usfca.edu/)
1. [Dynamo](http://s3.amazonaws.com/AllThingsDistributed/sosp/amazon-dynamo-sosp2007.pdf)
1. [Spring Boot](https://projects.spring.io/spring-boot/)
1. [Gson](https://mvnrepository.com/artifact/com.google.code.gson/gson/2.8.2)
1. [Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)
1. [Imgur](https://imgur.com/)
