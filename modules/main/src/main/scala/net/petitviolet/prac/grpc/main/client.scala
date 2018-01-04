package net.petitviolet.prac.grpc.main

import java.util.concurrent.TimeUnit

import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}
import org.slf4j.LoggerFactory
import proto.my_service.{MyServiceGrpc, RequestType, User}
import proto.my_service.MyServiceGrpc.MyServiceBlockingStub

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
    val blockingStub = MyServiceGrpc.blockingStub(channel)
    new GrpcClient(channel, blockingStub)
  }
}

class GrpcClient private(
                                private val channel: ManagedChannel,
                                private val blockingStub: MyServiceBlockingStub
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
    val users: Iterator[User] = blockingStub.listUser(new RequestType)
    logger.info(s"list user: ${users.toList}")
  }

  def add(name: String): Unit = rpc {
    val response = blockingStub.addUser(new User(name = name, age = 81))
    logger.info("add response: " + response.message)
  }
}