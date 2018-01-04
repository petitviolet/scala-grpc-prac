package net.petitviolet.prac.grpc.main

import io.grpc.stub.StreamObserver
import io.grpc.{Server, ServerBuilder}
import org.slf4j.{Logger, LoggerFactory}
import proto.my_service.{MyServiceGrpc, RequestType, ResponseType, User}
import proto.my_service.MyServiceGrpc.MyService

import scala.concurrent.Future

// ProtocolBufferから自動生成されたライブラリたち

import scala.concurrent.ExecutionContext

object server extends App {
  private def start(): Unit = {
    val server = new GrpcServer(ExecutionContext.global)
    server.start()
    server.blockUnitShutdown()
  }

  start()
}

class GrpcServer(executionContext: ExecutionContext) { self =>
  private val logger = LoggerFactory.getLogger(getClass)
  private val port = sys.env.getOrElse("SERVER_PORT", "50051").toInt
  private var server: Server = _

  def start(): Unit = {
    server = ServerBuilder.forPort(port).addService(
      MyServiceGrpc.bindService(new MyServiceImpl, executionContext)
    ).build.start
    logger.info("gRPC server started, listening on " + port)
    sys.addShutdownHook {
      logger.info("*** shutting down gPRC server since JVM is shutting down")
      self.stop()
    }
  }

  def stop(): Unit = {
    if (server != null) {
      logger.info("*** gRPC server shutdown")
      server.shutdown()
    }
  }

  def blockUnitShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

  private class MyServiceImpl extends MyService {
    import collection.mutable
    private val users: mutable.ListBuffer[User] = mutable.ListBuffer()

    override def listUser(request: RequestType, responseObserver: StreamObserver[User]): Unit = {
      println(s"request: ${request.toString}")
      users.foreach { responseObserver.onNext }
      responseObserver.onCompleted()
    }

    override def addUser(user: User): Future[ResponseType] = Future.successful {
      println(s"request: ${user.toString}")
      users += user
      new ResponseType(message = s"added $user")
    }
  }
}