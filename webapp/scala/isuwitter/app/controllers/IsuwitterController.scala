package controllers

import java.security.{MessageDigest, NoSuchAlgorithmException}

import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.mvc.{AbstractController, ControllerComponents}
import repository.UserRepository

case class LoginRequest(name: String, password: String)

object LoginRequest {
  val formMapping = mapping(
    "name" -> nonEmptyText,
    "password" -> nonEmptyText,
  )(LoginRequest.apply)(LoginRequest.unapply)
  val form = Form(formMapping)
}

@Singleton
class IsuwitterController @Inject()(cc: ControllerComponents, userRepository: UserRepository)(implicit assetsFinder: AssetsFinder) extends AbstractController(cc) {

  def index = Action { implicit request =>
    request.session.get("user_id").map { id =>
      Ok("Hello " + id)
    }.getOrElse {
      request.session.get("flush")
        .map { flush =>
          Ok(views.html.login(flush, null)).removingFromSession("flush")
        }.getOrElse {
        Ok(views.html.login(null, null)).removingFromSession("flush")
      }
    }
  }

  def login() = Action(parse.form(LoginRequest.form)) { implicit request =>
    val user = request.body
    val userOp = userRepository.findByUserName(user.name)
    if (userOp.isEmpty) {
      NotFound("not found")
    } else {
      val user = userOp.get
      var md: MessageDigest = null
      var digest: Array[Byte] = null
      try
        md = MessageDigest.getInstance("SHA-1")
      catch {
        case e: NoSuchAlgorithmException =>
          e.printStackTrace()
      }
      digest = md.digest((user.salt + user.password).getBytes)
      if (!(user.password == HexBin.encode(digest).toLowerCase)) {
        Found("/").withSession("flush" -> "ログインエラー")
      } else {
        Found("/").withSession("user_id" -> user.userId.toString)
      }
    }
  }
}
