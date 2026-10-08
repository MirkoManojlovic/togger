package com.togger.dep

import cats.effect.IO
import com.togger.account.{AccountRepoImpl, AccountService, AccountSessionRepo, AccountTypeAdminCheck, AccountTypeRepo}
import com.togger.auth.{ChangePasswordLogic, PasswordUpdate, SecuredEndpoint}
import com.togger.common.TgClockImpl
import com.togger.login.{LoginController, SessionTokenFactoryImpl}
import doobie.Transactor

class AuthDependenciesModule(val transactor: Transactor[IO]) {
  lazy val accountRepo = new AccountRepoImpl()
  lazy val accountSessionRepo = new AccountSessionRepo()
  lazy val adminCheck = new AccountTypeAdminCheck(accountRepo, new AccountTypeRepo(), transactor)
  lazy val sessionTokenFactory = new SessionTokenFactoryImpl
  lazy val tgClock = new TgClockImpl

  lazy val accountService =
    new AccountService(
      accountRepo = accountRepo,
      accountSessionRepo = accountSessionRepo,
      sessionTokenFactory = sessionTokenFactory,
      tgClock = tgClock,
      transactor = transactor
    )

  lazy val passwordUpdate = new PasswordUpdate()
  lazy val changePasswordLogic = new ChangePasswordLogic(accountRepo, passwordUpdate, tgClock, transactor)

  lazy val loginController = new LoginController(accountService, changePasswordLogic, securedEndpoint, tgClock)

  lazy val securityLogic = new SecurityLogicJvm(accountSessionRepo, transactor)
  lazy val securedEndpoint: SecuredEndpoint = new SecuredEndpoint(securityLogic)
}
