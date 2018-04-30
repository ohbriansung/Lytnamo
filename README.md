# CS682 Project5

## Introduction

This is a Key-value Distributed Storage System. Most of the concepts are inspired by Amazon's DynamoDB [\[2\]](#References). Included features: Consistent Hashing, Data Versioning, Synchronized Divergent Replicas, Membership, and Failure Detection/Handling. The system contains with one frontend services to handle requests/responses, and several backend services to store data.

## Features

### Consistent Hashing

Services will be added into the system manually. When a backend service is added to the system the frontend will add it into the membership list (the ring), and assign it with a token. The frontend service will automatically adjust the tokens of backend services and partition them into the ring.

### Data Versioning

My system provides enventual consistency, which allows for updates to be propagated to all replicas asynchronously. In order to capture causality between different versions of the same object, I use Vector Clock, whcih is effectively a list of (node address, timestamp) pairs.

### Synchronized Divergent Replicas

Each nodes will be storing a range of hashed key, and will replicate the data to N - 1 clockwise successor nodes in the ring. N can be configure in frontend service at anytime.

### Membership and Failure Detection

A gossip-based protocol propagates membership changes and maintains an eventually consistent view of membership. A backend service will keep sending gossip request to other backend services and frontend service. When a backend doesn't receive a request from a particular backend and timeout, it will notify the frontend and the frontend will remove the unreachable service and adjust the membership and the ring. Or a frontend will detect the timeout and failure by itself.

### Failure Handling

When a backend is unreachable, the frontend will send the data to the next backend and add a hint to that data. When a backend service receives the notification of failure from frontend, it will change its token, and send the request to  other backend services to retrive particular data base on the token. The hinted data will be put into a correct node after the partition of the ring.

## Milestones

* Environment setup and create frontend/backend services with basic feature: configure with pass-in arguments when service starts, and put/get methods APIs.
* Implementing Membership with gossip-base protocol.
* Implementing Consistent Hashing to support adding backend services and maintain the services in the ring.
* Developing testing framework for features above.
* Checkpoint on Tuesday **5/8/18** to complete and demonstrate the implementations above.
* Implementing Failure Decetion and Handling (removing).
* Implementing Replication.
* Implementing Data Versioning.
* Developing final testing framework.
* Demonstration before Wednesday **5/16/18**

## References
\[1\] [University of San Francisco](https://www.usfca.edu/)
\[2\] [Dynamo](http://s3.amazonaws.com/AllThingsDistributed/sosp/amazon-dynamo-sosp2007.pdf)