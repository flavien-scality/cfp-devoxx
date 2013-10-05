/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Association du Paris Java User Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package controllers

import models._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import ExecutionContext.Implicits.global
import play.api.data._
import play.api.data.Forms._

import notifiers.Mails
import play.api.libs.Crypto
import org.apache.commons.codec.binary.Base64

/**
 * Devoxx France Call For Paper main application.
 * @author Nicolas Martignole
 */
object Application extends Controller {

  def index = Action {
    implicit request =>
      Ok(views.html.Application.index(Authentication.loginForm))
  }

  def logout=Action{
    implicit request=>
      Redirect(routes.Application.index).withNewSession
  }

  def prepareSignup = Action {
    implicit request =>
      Ok(views.html.Application.prepareSignup(Authentication.newWebuserForm, Authentication.speakerForm))
  }

  def signup = Action {
    Ok("signup")
  }

  def newSpeaker = Action {
    Ok(views.html.Application.newUser(Authentication.newWebuserForm))
  }

  def forgetPassword=Action{
    Ok(views.html.Application.forgetPassword())
  }

  val emailForm=Form("email"->nonEmptyText)

  def doForgetPassword()=Action{
    implicit request=>
    emailForm.bindFromRequest.fold(error=>Redirect(routes.Application.forgetPassword), validEmail=>{
      Mails.sendResetPasswordLink(validEmail, routes.Application.resetPassword(Crypto.sign(validEmail.toLowerCase.trim),
       new String(Base64.encodeBase64(validEmail.toLowerCase.trim.getBytes("UTF-8")), "UTF-8")
      ).absoluteURL())
    })
    Redirect(routes.Application.index()).flashing("success"->"An email was sent to the provided email address. Please check your mailbox.")

  }

  def resetPassword(t:String, a:String)=Action{
    implicit request=>
      val email= new String(Base64.decodeBase64(a),"UTF-8")
      if(Crypto.sign(email)==t){
        val futureMaybeWebuser=Webuser.findByEmail(email)

        Async{
          futureMaybeWebuser.map{
            case Some(w)=>{
              val newPassword=Webuser.changePassword(w) // it is generated
              Redirect(routes.Application.index()).flashing("success"->("Your new password is "+newPassword + " (case-sensitive)"))
            }
            case _=>Redirect(routes.Application.index()).flashing("error"->"Sorry, this email is not registered in your system.")
          }
        }


      }else{
        Redirect(routes.Application.index()).flashing("error"->"Sorry, we could not validate your authentication token. Are you sure that this email is registered?")
      }
  }

  def findByEmail(email: String) = Action {
    implicit request =>
      Async {
        val futureResult: Future[Option[Webuser]] = Webuser.findByEmail(email)
        futureResult.map {
          maybeWebuser: Option[Webuser] =>
            Ok(views.html.Application.showWebuser(maybeWebuser))
        }
      }
  }

  def resetEnvForDev() = Action {
    implicit request =>
      Async {
        val futureResult: Future[Option[Webuser]] = Webuser.findByEmail("nicolas@martignole.net")
        futureResult.map {
          maybeWebuser =>
            maybeWebuser.map {
              webuser =>
                val err = Webuser.delete(webuser)
                Ok("Done " + err)
            }.getOrElse(NotFound("User does not exist"))
        }
      }
  }


}