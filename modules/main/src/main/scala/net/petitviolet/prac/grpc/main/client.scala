package net.petitviolet.prac.grpc.main

import java.util.concurrent.TimeUnit

import io.grpc._
import org.slf4j.LoggerFactory
import proto.my_service.{MyServiceGrpc, ListUserRequest, User}

object client extends App {
  def start(): Unit = {
    val client = GrpcClient("localhost", 50051)
    try {
      println("================")
      client.list()
      println("================")
      val name = args.headOption.getOrElse("alice")
      client.add(name)
      println("================")
      client.list()
      println("================")
    } finally {
      client.shutdown()
    }
  }
  start()
}

object GrpcClient {
  def apply(host: String, port: Int): GrpcClient = {
    val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
    val client: MyServiceGrpc.MyServiceBlockingClient = MyServiceGrpc.blockingStub(channel)
    new GrpcClient(channel, client)
  }
}

class GrpcClient private(
  private val channel: ManagedChannel,
  private val client: MyServiceGrpc.MyServiceBlockingClient
) {
  private val logger = LoggerFactory.getLogger(getClass)

  def shutdown(): Unit = {
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)
  }

  private def rpc[A](f: => A): A = {
    try {
      f
    }
    catch {
      case e: StatusRuntimeException =>
        logger.error(s"RPC failed: ${e.getStatus}", e)
        throw e
    }
  }

  def list(): Unit = rpc {
    val users: Iterator[User] = client.listUser(new ListUserRequest)
    logger.info(s"list user: ${users.toList}")
  }

  def add(name: String): Unit = rpc {
    val response = client.addUser(new User(name = "alice", age = 81))
    logger.info("response message: " + response.message)
  }
}