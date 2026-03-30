package com.togger.ui.project
import com.togger.style.TgCommonStyleJs.heightAndWidthFitContent
import com.togger.ui.auth._
import com.raquo.laminar.api.L._

class ShowAllProjectsComponent {

  case class EmployeeStatus(
    id: Long,
    fullName: String,
    status: Seq[Status]
  )

  case class Status(
    task: String,
    logEntries: Seq[String]
  )

  case class ShowAllProjectsResp(
    projectId: Long,
    projectName: String,
    employeesStatus: Seq[EmployeeStatus]
  )

  val resp = Seq(
    ShowAllProjectsResp(
      1,
      "Mlp",
      employeesStatus = Seq(
        EmployeeStatus(
          1,
          "Mirko Manojlovic",
          status = Seq(
            Status("task 1", logEntries = Seq("inprog", "inprog", "inprog", "blocked", "done"))
          )
        ),
        EmployeeStatus(
          2,
          "Dusan Manojlovic",
          status = Seq(
            Status("task 1", logEntries = Seq("inprog", "inprog", "inprog", "blocked", "done"))
          )
        )
      )
    )
  )

  val element = div(
    resp.map { dto =>
      TgCard
        .headerNoFooter(
          header = TgCard.CardHeader.plainText(dto.projectName, TgCard.CardHeaderSize.H3),
          body = TgCard.HtmlCardContent(
            div(
              dto.employeesStatus.map { employeeStatus =>
                div(
                  p(employeeStatus.fullName),
                  employeeStatus.status.map { status =>
                    div(p(status.task), status.logEntries.map(le => p(le)))
                  }
                )

              }
            )
          )
        )
        .element
        .amend(heightAndWidthFitContent)
    }
  )

}
