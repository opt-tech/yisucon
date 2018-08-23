package controllers

import java.io.{File, IOException}

import javax.inject._
import play.api.libs.json.Json
import play.api.mvc._
import repository.FriendsRepository
import play.api.data._
import play.api.data.Forms._

case class FriendRequest(user: String)

object FriendRequest {
  val formMapping = mapping(
    "user" -> nonEmptyText,
  )(FriendRequest.apply)(FriendRequest.unapply)
  val form = Form(formMapping)
}

@Singleton
class OptomoController @Inject()(cc: ControllerComponents, friendRepository: FriendsRepository)
  extends AbstractController(cc) {

  case class UserData(name: String, age: Int)

  val userForm = Form(
    mapping(
      "name" -> text,
      "age" -> number
    )(UserData.apply)(UserData.unapply)
  )

  def initialize = Action {
    initializer()
    Ok("ok")
  }

  def initializer() = {
    val builder = new ProcessBuilder("mysql", "-u", "root", "-D", "optomo", "-h", "127.0.0.1")
    builder.redirectInput(ProcessBuilder.Redirect.from(new File("../../sql/seed_optomo.sql")))

    try {
      val p = builder.start
      p.waitFor
      System.out.println(builder.redirectInput)
    } catch {
      case e@(_: IOException | _: InterruptedException) =>
        throw e
    }
  }

  def myFriends(me: String) = Action {
    val json = Json.obj("friends" -> Json.toJson(getFriends(me)))
    Ok(json)
  }

  def addFriend(me: String) = Action { implicit req =>
    var friends = getFriends(me)

    val user = FriendRequest.form.bindFromRequest().get.user

    if (friends.contains(user)) {
      BadRequest(user + " is already your friend.")
    } else {
      friends :+= user
      if (!updateFriends(me, friends.mkString(","))) {
        InternalServerError("error")
      } else {
        val json = Json.obj("friends" -> Json.toJson(friends))
        Ok(json)
      }
    }
  }

  def deleteFriend(me: String) = Action { implicit req =>
    var friends = getFriends(me)

    val user = FriendRequest.form.bindFromRequest().get.user

    if (!friends.contains(user)) {
      BadRequest(user + " not your friend.")
    } else {
      friends = friends.filter(_ != user)
      if (!updateFriends(me, friends.mkString(","))) {
        InternalServerError("error")
      } else {
        val json = Json.obj("friends" -> Json.toJson(friends))
        Ok(json)
      }
    }
  }

  def getFriends(name: String): List[String] = {
    val friends = friendRepository.findByUserName(name)
    friends.friends.split(",").toList
  }

  def updateFriends(name: String, friends: String): Boolean = {
    friendRepository.update(friends, name)
  }

}
