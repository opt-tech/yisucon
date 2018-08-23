package main

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"strings"

	_ "github.com/go-sql-driver/mysql"
	"github.com/gorilla/mux"
)

type Friend struct {
	ID      int64  `db:"id"`
	Me      string `db:"me"`
	Friends string `db:"friends"`
}

type DB struct {
	Host string
	Port string
	User string
	Pass string
	Name string
	Conn *sql.DB
}

var conn *DB

func (db *DB) initEnvs() error {

	db.Host = os.Getenv("OPTOMO_DB_HOST")
	db.Port = os.Getenv("OPTOMO_DB_PORT")
	db.User = os.Getenv("OPTOMO_DB_USER")
	db.Pass = os.Getenv("OPTOMO_DB_PASSWORD")
	db.Name = os.Getenv("OPTOMO_DB_NAME")

	if len(db.Host) == 0 {
		db.Host = "localhost"
	}

	if len(db.Port) == 0 {
		db.Port = "3306"
	}

	if len(db.User) == 0 {
		db.User = "root"
	}

	if len(db.Name) == 0 {
		db.Name = "optomo"
	}

	return nil
}

func (db *DB) dsn() string {
	return fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?charset=utf8mb4&parseTime=true&loc=Local&interpolateParams=true", db.User, db.Pass, db.Host, db.Port, db.Name)
}

func (db *DB) connect() error {
	err := db.initEnvs()

	if err != nil {
		return err
	}

	db.Conn, err = sql.Open("mysql", db.dsn())

	if err != nil {
		return err
	}

	return nil
}

func (db *DB) fetchFriend(user string) (*Friend, error) {

	friend := new(Friend)

	stmt, err := db.Conn.Prepare("SELECT * FROM friends WHERE me = ?")

	if err != nil {
		return nil, err
	}

	err = stmt.QueryRow(user).Scan(&friend.ID, &friend.Me, &friend.Friends)

	if err != nil {
		return nil, err
	}

	return friend, nil
}

func (db *DB) updateFriend(user, friends string) error {
	_, err := db.Conn.Exec("UPDATE friends SET friends = ? WHERE me = ?", friends, user)
	return err
}

func (friend *Friend) getFriends() []string {
	return strings.Split(friend.Friends, ",")
}

func getUserHandler(w http.ResponseWriter, r *http.Request) {

	me := mux.Vars(r)["me"]

	friend, err := conn.fetchFriend(me)
	if err != nil {
		errorResponseWriter(w, http.StatusBadRequest, err)
		return
	}

	friendJSON, err := json.Marshal(struct {
		Friends []string `json:"friends"`
	}{
		Friends: friend.getFriends(),
	})

	if err != nil {
		errorResponseWriter(w, http.StatusInternalServerError, err)
		return
	}

	w.WriteHeader(http.StatusOK)
	w.Write(friendJSON)

}

func postUserHandler(w http.ResponseWriter, r *http.Request) {

	me := mux.Vars(r)["me"]

	friend, err := conn.fetchFriend(me)
	if err != nil {
		errorResponseWriter(w, http.StatusBadRequest, err)
		return
	}

	data := struct {
		User string `json:"user"`
	}{}

	err = JSONUnmarshaler(r.Body, &data)

	if err != nil {
		errorResponseWriter(w, http.StatusBadRequest, err)
		return
	}

	for _, val := range friend.getFriends() {
		if strings.EqualFold(val, data.User) {

			errJSON, err := json.Marshal(struct {
				Error string `json:"error"`
			}{
				Error: data.User + " is already your friend.",
			})

			if err != nil {
				errorResponseWriter(w, http.StatusInternalServerError, err)
				return
			}

			w.WriteHeader(http.StatusBadRequest)
			w.Write(errJSON)
			return
		}
	}

	conn.updateFriend(me, friend.Friends+","+data.User)

	friendJSON, err := json.Marshal(struct {
		Friends []string `json:"friends"`
	}{
		Friends: append(friend.getFriends(), data.User),
	})

	if err != nil {
		errorResponseWriter(w, http.StatusInternalServerError, err)
		return
	}

	w.WriteHeader(http.StatusOK)
	w.Write(friendJSON)
}

func deleteUserHandler(w http.ResponseWriter, r *http.Request) {

	me := mux.Vars(r)["me"]

	friend, err := conn.fetchFriend(me)
	if err != nil {
		errorResponseWriter(w, http.StatusBadRequest, err)
		return
	}

	friends := friend.getFriends()

	data := struct {
		User string `json:"user"`
	}{}

	err = JSONUnmarshaler(r.Body, &data)

	if err != nil {
		errorResponseWriter(w, http.StatusBadRequest, err)
		return
	}

	for _, val := range friends {
		if strings.EqualFold(val, data.User) {

			friends = remove(friends, data.User)

			conn.updateFriend(me, strings.Join(friends, ","))

			friendJSON, err := json.Marshal(struct {
				Friends []string `json:"friends"`
			}{
				Friends: friends,
			})

			if err != nil {
				errorResponseWriter(w, http.StatusInternalServerError, err)
				return
			}

			w.WriteHeader(http.StatusOK)
			w.Write(friendJSON)
			return
		}
	}

	errJSON, err := json.Marshal(struct {
		Error string `json:"error"`
	}{
		Error: data.User + " is not your friend.",
	})

	if err != nil {
		errorResponseWriter(w, http.StatusInternalServerError, err)
		return
	}

	w.WriteHeader(http.StatusBadRequest)
	w.Write(errJSON)
	return

}

func remove(array []string, tval string) []string {
	result := []string{}
	for _, v := range array {
		if v != tval {
			result = append(result, v)
		}
	}
	return result
}

func errorResponseWriter(w http.ResponseWriter, status int, err error) {
	w.WriteHeader(status)
	w.Write([]byte(err.Error()))
}

func JSONUnmarshaler(body io.Reader, i interface{}) error {

	bufbody := new(bytes.Buffer)

	length, err := bufbody.ReadFrom(body)

	if err != nil && err != io.EOF {
		return err
	}

	if err = json.Unmarshal(bufbody.Bytes()[:length], &i); err != nil {
		return err
	}

	return nil
}
func initializeHandler(w http.ResponseWriter, r *http.Request) {
	path, err := exec.LookPath("mysql")
	if err != nil {
		errorResponseWriter(w, http.StatusInternalServerError, err)
		return
	}

	exec.Command(path, "-u", "root", "-D", "optomo", "<", "../../sql/seed_optomo2.sql").Run()
	if err != nil {
		errorResponseWriter(w, http.StatusInternalServerError, err)
		return
	}

	resultJSON, err := json.Marshal(struct {
		Result []string `json:"result"`
	}{
		Result: []string{"ok"},
	})
	if err != nil {
		errorResponseWriter(w, http.StatusInternalServerError, err)
		return
	}

	w.WriteHeader(http.StatusOK)
	w.Write(resultJSON)
	return
}

func NewRouter() *mux.Router {

	router := mux.NewRouter().StrictSlash(true)

	router.Methods(http.MethodGet).Path("/initialize").HandlerFunc(initializeHandler)
	router.Methods(http.MethodGet).Path("/{me}").HandlerFunc(getUserHandler)
	router.Methods(http.MethodPost).Path("/{me}").HandlerFunc(postUserHandler)
	router.Methods(http.MethodDelete).Path("/{me}").HandlerFunc(deleteUserHandler)

	return router
}

func main() {

	conn = new(DB)

	err := conn.connect()

	if err != nil {
		log.Fatal(err)
	}

	log.Fatalln(http.ListenAndServe(":8081", NewRouter()))
}
