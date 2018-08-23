package db

import (
	"errors"
	"fmt"
	"os"
	"time"

	"github.com/yahoojapan/yisucon/benchmarker/model"

	_ "github.com/go-sql-driver/mysql"
	"github.com/gocraft/dbr"
)

type DB struct {
	Type string
	Host string
	Port string
	User string
	Pass string
	Name string
	Conn *dbr.Session
}

func (db *DB) dsn() string {
	return fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?charset=utf8mb4&parseTime=true&loc=Local", db.User, db.Pass, db.Host, db.Port, db.Name)
}

func (db *DB) connect() error {
	db.Type = "mysql"

	db.Host = os.Getenv("OPT_ISUCON_DB_HOST")
	db.Port = os.Getenv("OPT_ISUCON_DB_PORT")
	db.User = os.Getenv("OPT_ISUCON_DB_USER")
	db.Pass = os.Getenv("OPT_ISUCON_DB_PASSWORD")
	db.Name = os.Getenv("OPT_ISUCON_DB_NAME")

	if len(db.Host) == 0 || len(db.Port) == 0 || len(db.User) == 0 || len(db.Name) == 0 {
		db.Host = "localhost"
		db.Port = "3306"
		db.User = "root"
		db.Pass = ""
		db.Name = "isucon"
	}

	conn, err := dbr.Open(db.Type, db.dsn(), nil)

	if err != nil {
		return err
	}

	db.Conn = conn.NewSession(nil)

	return db.Conn.Ping()
}

func (db *DB) close() error {
	return db.Conn.Close()
}

func NewDB() (*DB, error) {

	db := new(DB)

	err := db.connect()

	if err != nil {
		return nil, err
	}

	return db, nil
}

func (db DB) FetchQueue() (*model.TeamQueue, error) {

	tx, err := db.Conn.Begin()

	if err != nil {
		return nil, err
	}

	defer tx.RollbackUnlessCommitted()

	var queue *model.TeamQueue

	//INFO : queue table status { 0: done, 1: standby, 2: running }
	err = tx.Select("*").From("team_queue").Where(dbr.Eq("status", 1)).OrderBy("date").Limit(1).LoadStruct(&queue)

	if err != nil {
		return nil, err
	}

	result, err := tx.Update("queue").Set("status", 2).Where(dbr.And(dbr.Eq("team_id", queue.TeamID), dbr.Eq("status", 1))).Exec()

	if err != nil {
		return nil, err
	}

	if count, _ := result.RowsAffected(); count != 1 {
		return nil, dbr.ErrNotSupported
	}

	err = tx.Commit()

	if err != nil {
		return nil, err
	}

	return queue, nil
}

func (db DB) expiredQueueChecker() {

	tx, err := db.Conn.Begin()

	if err != nil {
		return
	}

	defer tx.RollbackUnlessCommitted()

	_, err = tx.Exec("UPDATE queue SET status = 0 WHERE status = 2 AND date < NOW() - INTERVAL 2 MINUTE")

	if err != nil {
		return
	}

	err = tx.Commit()

	if err != nil {
		return
	}
}

// QueueChecker returns benchmark queue
func QueueChecker(dur time.Duration) (*model.TeamQueue, error) {

	db, err := NewDB()

	if err != nil {
		return nil, err
	}

	defer db.close()

	ticker := time.NewTicker(dur)

	defer ticker.Stop()

	for {
		select {
		case <-ticker.C:
			db.expiredQueueChecker()
			queue, err := db.FetchQueue()
			if queue != nil && err == nil {
				return queue, nil
			}
		}
	}
}

// SaveResult saves result to database
func SaveResult(teamID int64, score *model.Score) error {
	db, err := NewDB()

	if err != nil {
		return err
	}

	defer db.close()

	score.CreateErrMessage()

	tx, err := db.Conn.Begin()

	if err != nil {
		return err
	}

	defer tx.RollbackUnlessCommitted()

	result, err := tx.Update("queue").Set("status", 0).Where(dbr.Eq("team_id", teamID)).Exec()

	if err != nil {
		return err
	}

	if count, _ := result.RowsAffected(); count > 1 {
		return errors.New("too many update request : may be bad logic")
	}

	_, err = tx.InsertInto("score").Columns("queue_id", "score", "message").Values(score.QueueID.Int64, score.Score.Int64, score.Message.String).Exec()

	if err != nil {
		return err
	}

	err = tx.Commit()

	if err != nil {
		return err
	}

	return nil
}
