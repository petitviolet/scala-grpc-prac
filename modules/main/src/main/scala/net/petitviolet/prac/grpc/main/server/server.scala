package net.petitviolet.prac.grpc.main.server

import java.util.concurrent.Executors

import io.grpc.stub.StreamObserver
import io.grpc.{ Server, ServerBuilder, ServerInterceptors }
import net.petitviolet.prac.grpc.model
import net.petitviolet.operator._
import net.petitviolet.prac.grpc.protocol.MyService._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object server extends App {
  private val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private def start(): Unit = {
    val server = new GrpcServer(executionContext)
    server.start()
    server.blockUnitShutdown()
  }

  start()
}

class GrpcServer(executionContext: ExecutionContext) {
  self =>
  private val logger = new MyLogger(this.getClass.getName)
  private val port = sys.env.getOrElse("SERVER_PORT", "50051").toInt
  private var server: Server = _

  private lazy val myServiceImpl = new MyServiceImpl with model.MixInOrganizationRepository with model.MixInEmployeeRepository

  def start(): Unit = {
    server = ServerBuilder.forPort(port).addService(
      ServerInterceptors.intercept(
        MyServiceGrpc.bindService(myServiceImpl, executionContext),
        AccessLogger
      )
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

  private abstract class MyServiceImpl extends MyServiceGrpc.MyService
    with model.UsesEmployeeRepository with model.UsesOrganizationRepository {

    private implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))

    // unary
    override def showOrganization(request: ShowOrganizationRequest): Future[Organization] = {
      organizationRepository.findById(request.organizationId)
        .flatMap {
          case Some(organization) =>
            employeeRepository.findByOrganization(organization).map { employees =>
              (organization, employees)
            }
          case None =>
            throw new RuntimeException(s"invalid request organization id = ${ request.organizationId }")
        }.map { case (organization, employees) =>
        Organization(organization.id, organization.name, employees.map { employee =>
          Employee(employee.name, employee.age, employee.organization.id)
        })
      }
    }

    // server stream
    override def showEmployees(request: ShowEmployeeRequest, responseObserver: StreamObserver[Employee]): Unit = {
      Option(request.organizationId).filterNot { _ == 0 }.map { orgId =>
        organizationRepository.findById(orgId).map {
          case Some(organization) => organization
          case None =>
            throw new RuntimeException(s"invalid request organization id = $orgId")
        }
      } map { organizationF: Future[model.Organization] =>
        organizationF flatMap { employeeRepository.findByOrganization }
      } getOrElse {
        employeeRepository.findAll()
      } map { employees: Seq[model.Employee] =>
        employees.foreach { employee: model.Employee =>
          val protoEmployee = Employee(employee.name, employee.age, employee.organization.id)
          responseObserver.onNext(protoEmployee)
        }
      } onComplete {
        case Success(_) =>
          responseObserver.onCompleted()
        case Failure(t) =>
          responseObserver.onError(t)
      }
    }

    // client stream
    override def addEmployee(responseObserver: StreamObserver[MessageResponse]): StreamObserver[Employee] = {
      new StreamObserver[Employee] {
        @volatile private var employees: Seq[model.Employee] = Nil
        override def onError(t: Throwable): Unit = {
          logger.error("addEmployee onError", t)
          responseObserver.onError(t)
        }

        override def onCompleted(): Unit = {
          logger.info("addEmployee onCompleted")
          val names = employees.reverse.map { _.name }.mkString(", ")
          responseObserver.onNext(MessageResponse(s"addEmployee succeeded store name = ${ names }"))
          responseObserver.onCompleted()
        }

        override def onNext(employee: Employee): Unit = {
          organizationRepository.findById(employee.organizationId)
            .flatMap {
              case Some(organization) =>
                val mEmployee = model.Employee.create(employee.name, employee.age, organization)
                employees = mEmployee +: employees
                logger.info(s"===========sleeping...")
                Thread.sleep(1000L)
                logger.info(s"===========awake!")
                employeeRepository.store(mEmployee)
              case None =>
                throw new RuntimeException(s"addEmployee invalid request organization id = ${ employee.organizationId }")
            } onComplete {
            case Success(_) =>
              logger.info(s"addEmployee onNext: ${employee.name}")
            case Failure(t) =>
              onError(t)
          }
        }
      }
    }

    // bidirectional stream
    override def lottery(responseObserver: StreamObserver[Employee]): StreamObserver[FetchRandomRequest] = {
      new StreamObserver[FetchRandomRequest] {
        override def onError(t: Throwable): Unit = {
          logger.error(s"lottery onError", t)
        }

        override def onCompleted(): Unit = {
          logger.info(s"lottery onCompleted")
          responseObserver.onCompleted()
        }

        override def onNext(value: FetchRandomRequest): Unit = {
          val allF = employeeRepository.findAll()
          allF.foreach { _.headOption foreach { em =>
            logger.info(s"lottery $em")
            responseObserver.onNext(Employee(em.name, em.age, em.organization.id))
          }
        }
      }
    }
  }
}