package net.petitviolet.prac.grpc.main

import java.util.concurrent.TimeUnit

import io.grpc._
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import proto.my_service._

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
    val org = client.showOrganization(new ShowOrganizationRequest(organizationId = 2))
    logger.info(s"org-2: $org")
    val allEmployees = client.showEmployees(new ShowEmployeeRequest())
    logger.info(s"all employees: ${allEmployees.toList}")
    val employees = client.showEmployees(new ShowEmployeeRequest(organizationId = 2))
    logger.info(s"org-2 employees: ${employees.toList}")
  }

  def add(name: String): Unit = rpc {
    logger.info(s"start add")
    val asyncClient = MyServiceGrpc.stub(channel)
    val observer = new StreamObserver[MessageResponse] {
      override def onError(t: Throwable): Unit = logger.error("failed to add employee", t)

      override def onCompleted(): Unit = logger.info("completed to add employee")

      override def onNext(value: MessageResponse): Unit = logger.info(s"onNext. message = ${value.message}")
    }
    val employeeObserver: StreamObserver[Employee] = asyncClient.addEmployee(observer)
    (1 to 5).foreach { i =>
      val employee = Employee(s"${name}-$i", i * 10, i)
      employeeObserver.onNext(employee)
    }
    employeeObserver.onCompleted()
    logger.info(s"add completed")
  }
}