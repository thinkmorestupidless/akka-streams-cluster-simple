
## A Simple Cluster Example

Demonstrates running an Akka stream with a `Sink.actorRefWithAck`

To run this sample, type `sbt run` 

`SimpleClusterApp` starts three actor systems (cluster members) in the same JVM process. It can be more interesting to run them in separate processes. Stop the application and then open three terminal windows.

In the first terminal window, start the first seed node with the following command:

    sbt "runMain sample.cluster.simple.SimpleClusterApp 2551"

2551 corresponds to the port of the first seed-nodes element in the configuration. In the log output you see that the cluster node has been started and changed status to 'Up'.

In the second terminal window, start the second seed node with the following command:

    sbt "runMain sample.cluster.simple.SimpleClusterApp 2552"

2552 corresponds to the port of the second seed-nodes element in the configuration. In the log output you see that the cluster node has been started and joins the other seed node and becomes a member of the cluster. Its status changed to 'Up'.

Switch over to the first terminal window and see in the log output that the member joined.

Start another node in the third terminal window with the following command:

    sbt "runMain sample.cluster.simple.SimpleClusterApp 0"

## Removing the Ack

Removing the Ack from `SimpleActor`'s receive (when receiving a `SimpleMessage`) will now cause the stream to block on the first `SimpleMessage` received - no Ack is sent by the ShardRegion.