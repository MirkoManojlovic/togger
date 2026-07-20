package com.togger.routes

import com.togger.ui.auth.LoginRepo

import scala.util.chaining.scalaUtilChainingOps

class AuthenticationRouteGuard(
  loginRepo: LoginRepo,
  loginPage: FrontendPage,
  homePage: FrontendPage
) {

  def canVisitPage(page: FrontendPage): FrontendPage =
    userNotAuthenticated
      .orElse(userAuthenticatedVisitingLoginPage)
      .orElse(userAuthenticated(page))
      .pipe(pageFilter => pageFilter(page))

  private val userNotAuthenticated: PartialFunction[FrontendPage, FrontendPage] = {
    case page if page.needsAuth && loginRepo.notAuthenticated =>
      loginPage
  }

  private val userAuthenticatedVisitingLoginPage: PartialFunction[FrontendPage, FrontendPage] =
    new PartialFunction[FrontendPage, FrontendPage] {
      override def isDefinedAt(page: FrontendPage): Boolean =
        page == loginPage && loginRepo.authenticated

      override def apply(page: FrontendPage): FrontendPage = homePage
    }

  private def userAuthenticated(page: FrontendPage): PartialFunction[FrontendPage, FrontendPage] = { case _ =>
    page
  }

}
