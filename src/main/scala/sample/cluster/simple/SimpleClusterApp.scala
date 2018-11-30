package sample.cluster.simple

import akka.NotUsed
import com.typesafe.config.ConfigFactory
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}

object SimpleClusterApp {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("2551", "2552", "0"))
    else
      startup(args)
  }

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>

      // Override the configuration of the port
      val config = ConfigFactory.parseString(s"""
        akka.remote.artery.canonical.port=$port
        """).withFallback(ConfigFactory.load())

      // Create an Akka system
      implicit val system = ActorSystem("ClusterSystem", config)
      implicit val materializer = ActorMaterializer()
      // Create an actor that handles cluster domain events
      val actor = system.actorOf(Props[SimpleActors], name = "clusterListener")

      if (port == "0") {
        val numbers: Source[SimpleMessage, NotUsed] =
          Source(1 to 10).map(i => SimpleMessage(i, i))

        // sent from actor to stream to "ack" processing of given element
        val AckMessage = Ack

        // sent from stream to actor to indicate start, end or failure of stream:
        val InitMessage = StreamInitialized
        val OnCompleteMessage = StreamCompleted
        val onErrorMessage = (ex: Throwable) ⇒ StreamFailure(ex)

        val sink = Sink.actorRefWithAck(
          actor,
          onInitMessage = InitMessage,
          ackMessage = AckMessage,
          onCompleteMessage = OnCompleteMessage,
          onFailureMessage = onErrorMessage
        )

        numbers
          .runWith(sink)
      }
    }
  }

}

class SimpleActors extends Actor with ActorLogging {

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ SimpleMessage(id, _) => (id.toString, msg)
  }

  private val numberOfShards = 100

  private val extractShardId: ShardRegion.ExtractShardId = {
    case SimpleMessage(id, _) => (id % numberOfShards).toString
    // Needed if you want to use 'remember entities':
    //case ShardRegion.StartEntity(id) => (id.toLong % numberOfShards).toString
  }

  val deviceRegion: ActorRef = ClusterSharding(context.system).start(
    typeName = "Device",
    entityProps = Props[SimpleActor],
    settings = ClusterShardingSettings(context.system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)

  def receive: Receive = {

    case StreamInitialized ⇒
      log.info("Stream initialized!")
      sender() ! Ack // ack to allow the stream to proceed sending more elements

    case el: SimpleMessage ⇒
      deviceRegion forward el

    case StreamCompleted ⇒
      log.info("Stream completed!")

    case StreamFailure(ex) ⇒
      log.error(ex, "Stream failed!")
  }
}

class SimpleActor extends Actor with ActorLogging {

  def receive: Receive = {
    case el: SimpleMessage ⇒
      log.info("Received element: {} => {}", el.id, el.number)
      sender() ! Ack // ack to allow the stream to proceed sending more elements
  }
}

case object Ack
case object StreamInitialized
final case class SimpleMessage(id: Int, number: Int)
case object StreamCompleted
final case class StreamFailure(ex: Throwable)

