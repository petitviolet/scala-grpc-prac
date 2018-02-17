package net.petitviolet.prac.grpc.main

import java.util.concurrent.{ CountDownLatch, Executors, TimeUnit }

import io.grpc._
import io.grpc.stub.StreamObserver
import net.petitviolet.prac.grpc.protocol.MyService._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future, Promise }

object client extends App {
  def start(): Unit = {
    val client = GrpcClient("localhost", 50051)(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))
    def sleep(milli: Long) = {
      println(s"sleeping...")
      Thread.sleep(milli)
      println(s"awake!")
    }
    try {
      println("================")
      client.blockingShow()
      sleep(1000L)
      println("================")

      val name = args.headOption.getOrElse("alice")
      client.addEmployee(name)
      sleep(1000L)
      println("================")

      Await.ready(client.showEmployees(), Duration.Inf)
      sleep(1000L)

      println("================")
      client.lottery()
      sleep(1000L)
      println("================")

    } finally {
      Thread.sleep(1000L)
      client.shutdown()
    }
  }

  start()
}

object GrpcClient {
  def apply(host: String, port: Int)(implicit ec: ExecutionContext): GrpcClient = {
    val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
    new GrpcClient(channel)
  }
}

class GrpcClient private(
  private val channel: ManagedChannel,
)(implicit val ec: ExecutionContext) {
  private val blockingClient: MyServiceGrpc.MyServiceBlockingClient = MyServiceGrpc.blockingStub(channel)
  private val asyncClient: MyServiceGrpc.MyServiceStub = MyServiceGrpc.stub(channel)
  private val logger = LoggerFactory.getLogger(getClass)

  def shutdown(): Unit = {
    logger.info(s"client shutting down...")
    channel.awaitTermination(5, TimeUnit.SECONDS)
    channel.shutdown()
    logger.info(s"client shutting down completed: ${channel.isShutdown}")
  }

  private def rpc[A](f: => A): A = {
    try {
      f
    }
    catch {
      case e: StatusRuntimeException =>
        logger.error(s"RPC failed: ${ e.getStatus }", e)
        throw e
    }
  }

  // unary, server streaming
  def blockingShow(): Unit = rpc {
    val orgF: Future[Organization] = asyncClient.showOrganization(new ShowOrganizationRequest(organizationId = 2))
    logger.info(s"orgF: ${Await.result(orgF, Duration.Inf)}")
    val org: Organization = blockingClient.showOrganization(new ShowOrganizationRequest(organizationId = 2))
    logger.info(s"org-2: $org")
    val allEmployees = blockingClient.showEmployees(new ShowEmployeeRequest())
    logger.info(s"all employees: ${ allEmployees.toList }")
    val employees = blockingClient.showEmployees(new ShowEmployeeRequest(organizationId = 2))
    logger.info(s"org-2 employees: ${ employees.toList }")
  }

  // client streaming
  def addEmployee(name: String) = rpc {
    logger.info(s"start add")
    val responseObserver = new StreamObserver[MessageResponse] {
      override def onError(t: Throwable): Unit =
        logger.error("add failed to add employee", t)

      override def onCompleted(): Unit =
        logger.info("add completed to add employee")

      override def onNext(value: MessageResponse): Unit = {
        logger.info(s"add onNext. message = ${ value.message }}")
      }
    }

    val requestObserver: StreamObserver[Employee] = asyncClient.addEmployee(responseObserver)
    (1 to 3).foreach { i =>
      val employee = Employee(s"${ name }-$i", i * 10, i)
      requestObserver.onNext(employee)
    }

    requestObserver.onCompleted() // don't forget
    responseObserver.onCompleted()
  }

  // server streaming
  def showEmployees(): Future[Unit] = rpc {
    val promise = Promise[Unit]()
    val responseObserver = new StreamObserver[Employee] {
      override def onError(t: Throwable): Unit = {
        logger.error(s"showEmployee onError", t)
      }
      override def onCompleted(): Unit = {
        logger.info(s"showEmployee onComplete")
        promise.success(())
      }

      override def onNext(value: Employee): Unit = {
        logger.info(s"showEmployee onNext: $value")
      }
    }
    asyncClient.showEmployees(new ShowEmployeeRequest(), responseObserver)
    promise.future
  }

  // bidirectional streaming
  def lottery(): Future[Unit] = rpc {
    logger.info(s"lottery start")
    val promise = Promise[Unit]()
    val responseObserver = new StreamObserver[Employee] {
      override def onError(t: Throwable): Unit = {
        logger.error(s"lottery onError", t)
        promise.failure(t)
      }
      override def onCompleted(): Unit = {
        logger.info(s"lottery onComplete")
        promise.success(())
      }

      override def onNext(value: Employee): Unit = {
        logger.info(s"lottery onNext: $value")
      }
    }
    val requestObserver: StreamObserver[FetchRandomRequest] = asyncClient.lottery(responseObserver)

    (1 to 3).foreach { i =>
      logger.info(s"lottery count: $i")
      requestObserver.onNext(FetchRandomRequest())
    }

    requestObserver.onCompleted()
    promise.future
  }

}