package net.petitviolet.prac.grpc.main

import io.grpc.{Server, ServerBuilder}

// ProtocolBufferから自動生成されたライブラリたち
import users.users.{RequestType, UsersGrpc}

import scala.concurrent.ExecutionContext

object main extends App {
  private val logger = Logger.getLogger(classOf[GrpcServer].getName)

  def main(args: Array[String]): Unit = {
    val server = new GrpcServer(ExecutionContext.global)
    server.start()
    server.blockUnitShutdown()
  }

  private val port = sys.env.getOrElse("SERVER_PORT", "50051").asInstanceOf[String].toInt
}

class GrpcServer(executionContext: ExecutionContext) { self =>
  private val port = sys.env.getOrElse("SERVER_PORT", "50051").asInstanceOf[String].toInt
  private[this] var server: Server = null

  def start(): Unit = {
    server = ServerBuilder.forPort(port).addService(
      UsersGrpc.bindService(new UsersImpl, executionContext)
    ).build.start
    Logger.info("gRPC server started, listening on " + port)
    sys.addShutdownHook {
      Logger.info("*** shutting down gPRC server since JVM is shutting down")
      self.stop()
    }
  }

  def stop(): Unit = {
    if (server != null) {
      Logger.info("*** gRPC server shutdown")
      server.shutdown()
    }
  }

  def blockUnitShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

  private class UsersImpl extends UsersGrpc.Users {
    /* 中略 */
  }
}