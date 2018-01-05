package net.petitviolet.prac.grpc.main

import java.util.concurrent.Executors

import io.grpc.stub.StreamObserver
import io.grpc.{Server, ServerBuilder}
import net.petitviolet.prac.grpc.model
import org.slf4j.LoggerFactory
import proto.my_service._

import scala.concurrent.Future

import scala.concurrent.ExecutionContext

object server extends App {
  private val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private def start(): Unit = {
    val server = new GrpcServer(executionContext)
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

  private class MyServiceImpl extends MyServiceGrpc.MyService with model.MixInUserRepository {
    private implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))

    override def listUser(request: RequestType, responseObserver: StreamObserver[User]): Unit = {
      println(s"request: ${request.toString}")
      userRepository.findAll().foreach { users: Seq[model.User] =>
        users.foreach { user =>
          val grpcUser = new User(user.name, user.age)
          responseObserver.onNext(grpcUser)
        }
        responseObserver.onCompleted()
      }
    }

    override def addUser(user: User): Future[ResponseType] = {
      println(s"request: ${user.toString}")
      val mUser: model.User = model.User.create(user.name, user.age)
      userRepository.store(mUser).map { _ =>
        new ResponseType(message = s"added $mUser")
      }
    }
  }
}