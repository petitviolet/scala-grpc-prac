package net.petitviolet.prac.grpc.main

import java.util.concurrent.{ CountDownLatch, Executors, TimeUnit }

import io.grpc._
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import proto.my_service._
import net.petitviolet.operator._

import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success }

object client extends App {
  def start(): Unit = {
    val client = GrpcClient("localhost", 50051)(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))
    try {
      println("================")
      client.list()
      println("================")
      val name = args.headOption.getOrElse("alice")
      Await.ready(client.add(name), Duration.Inf)
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
  def apply(host: String, port: Int)(implicit ec: ExecutionContext): GrpcClient = {
    val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build
    new GrpcClient(channel)
  }
}

class GrpcClient private(
  private val channel: ManagedChannel,
)(implicit val ec: ExecutionContext) {
  private val blockingClient: MyServiceGrpc.MyServiceBlockingClient = MyServiceGrpc.blockingStub(channel)
  private val asyncClient = MyServiceGrpc.stub(channel)
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
    val org = blockingClient.showOrganization(new ShowOrganizationRequest(organizationId = 2))
    logger.info(s"org-2: $org")
    val allEmployees = blockingClient.showEmployees(new ShowEmployeeRequest())
    logger.info(s"all employees: ${allEmployees.toList}")
    val employees = blockingClient.showEmployees(new ShowEmployeeRequest(organizationId = 2))
    logger.info(s"org-2 employees: ${employees.toList}")
  }

  def add(name: String): Future[Unit] = rpc {
    logger.info(s"start add")
    val finishLatch = new CountDownLatch(1)
    val responseObserver = new StreamObserver[MessageResponse] {
      override def onError(t: Throwable): Unit = {
        logger.error("failed to add employee", t)
        finishLatch.countDown()
      }

      override def onCompleted(): Unit = {
        logger.info("completed to add employee")
        finishLatch.countDown()
      }

      override def onNext(value: MessageResponse): Unit = logger.info(s"onNext. message = ${ value.message }")
    }

    Future {
      val requestObserver: StreamObserver[Employee] = asyncClient.addEmployee(responseObserver)
      try {
        (1 to 5).foreach { i =>
          val employee = Employee(s"${ name }-$i", i * 10, i)
          requestObserver.onNext(employee)
        }
        logger.info(s"waiting...")
        finishLatch.await(500, TimeUnit.MILLISECONDS)
        logger.info(s"finish!")
        requestObserver.onCompleted()
      } catch {
        case t: Throwable =>
          logger.error(s"failed to add employee...", t)
          requestObserver.onError(t)
      } finally {
        logger.info(s"add completed")
      }
    } <| {
      _ onComplete {
        case Success(()) =>
          logger.info("succeeded add Future")
        case Failure(t) =>
          logger.error("failed to add Future")
          throw t
      }
    }
  }
}