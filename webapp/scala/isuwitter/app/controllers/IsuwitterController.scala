package controllers

import java.security.{MessageDigest, NoSuchAlgorithmException}
import java.time.format.DateTimeFormatter

import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin
import javax.inject.{Inject, Singleton}
import model.Tweet
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.{AbstractController, ControllerComponents}
import repository.{TweetRepository, UserRepository}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.control.Breaks

case class LoginRequest(name: String, password: String)

object LoginRequest {
  val formMapping = mapping(
    "name" -> text,
    "password" -> text,
  )(LoginRequest.apply)(LoginRequest.unapply)
  val form = Form(formMapping)
}

@Singleton
class IsuwitterController @Inject()(cc: ControllerComponents, userRepository: UserRepository, tweetRepository: TweetRepository, ws: WSClient)(implicit assetsFinder: AssetsFinder) extends AbstractController(cc) {

  val PER_PAGE = 50

  def index = Action { implicit request =>
    request.session.get("user_id").map { id =>
      val name = getUserName(Some(id.toInt))
      val friends = loadFriend(name)
      var rows: Seq[Tweet] = Seq()

      //TODO
      rows = tweetRepository.findOrderByCreatedAtDesc()

      var tweets = Seq[Tweet]()
      val b = new Breaks
      b.breakable {
        for (row <- rows) {
          var tweet = new Tweet(row.userId)
          tweet = tweet.copy(html = htmlify(row.text))
          tweet = tweet.copy(time = row.createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
          val friendName = getUserName(Some(row.userId))
          tweet = tweet.copy(userName = friendName)
          if (friends.contains(friendName)) {
            tweets = tweets :+ tweet
          }
          if (tweets.size == PER_PAGE) b.break
        }
      }
      Ok(views.html.index(name, tweets)).removingFromSession("flush")
    }.getOrElse {
      request.session.get("flush")
        .map { flush =>
          Ok(views.html.login(flush, null)).removingFromSession("flush")
        }.getOrElse {
        Ok(views.html.login(null, null)).removingFromSession("flush")
      }
    }
  }

  def htmlify(text: String): String = {
    if (text == null) return ""
    text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("'", "&apos;").replaceAll("\"", "&quot;").replaceAll("#(\\S+)(\\s|$)", "<a class=\"hashtag\" href=\"/hashtag/$1\">#$1</a>$2")
  }

  def getUserName(userId: Option[Int]): String = {
    if (userId.isEmpty) return null
    val user = userRepository.findByUserId(userId.get)
    user.get.userName
  }

  def user(user: String, _until: Option[String], _append: Option[Int]) = Action { implicit request =>
    val until = _until.getOrElse("")
    val append = _append.getOrElse(0)
    val id = request.session.get("user_id").map(_.toInt)
    val name = getUserName(id)
    var mypage: Boolean = false
    if (user == name) mypage = true
    val userId_ = getUserId(user)
    if (userId_.isEmpty) {
      NotFound("not found")
    } else {
      val userId = userId_.get
      var isFriend: Boolean = false
      if (name != null) {
        val friends = loadFriend(name)
        if (friends.contains(user)) isFriend = true
      }
      var rows = if (until.length == 0) {
        tweetRepository.findByUserIdOrderByCreatedAtDesc(userId)
      } else {
        tweetRepository.findByUserIdOrderByCreatedAtDesc(userId, until)
      }

      var tweets = Seq[Tweet]()
      val b = new Breaks
      b.breakable {
        for (row <- rows) {
          var tweet = new Tweet(row.userId)
          tweet = tweet.copy(html = htmlify(row.text))
          tweet = tweet.copy(time = row.createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
          tweet = tweet.copy(userName = user)
          tweets = tweets :+ tweet
          if (tweets.size == PER_PAGE) b.break
        }
      }
      if (append == 0) {
        Ok(views.html.user(name, user, mypage, isFriend, tweets))
      } else {
        Ok(views.html._tweet(tweets))
      }
    }
  }

  private def getUserId(userName: String): Option[Int] = {
    if (userName == null) return None
    val user = userRepository.findByUserName(userName)
    user.map(_.userId)
  }

  def login() = Action(parse.form(LoginRequest.form)) { implicit request =>
    val userForm = request.body
    val userOp = userRepository.findByUserName(userForm.name)
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
      digest = md.digest((user.salt + userForm.password).getBytes)
      if (!(user.password == HexBin.encode(digest).toLowerCase)) {
        Found("/").withSession("flush" -> "ログインエラー")
      } else {
        Found("/").withSession("user_id" -> user.userId.toString)
      }
    }
  }

  def loadFriend(name: String): Seq[String] = {
    val request: WSRequest = ws.url("http://localhost:8081/" + name)
    val complexRequest: WSRequest = request.addHttpHeaders("Accept" -> "application/json")
    val response = Await.result(complexRequest.get(), Duration.Inf)
    val json = response.json
    (json \ "friends").as[Seq[String]]
  }
}
