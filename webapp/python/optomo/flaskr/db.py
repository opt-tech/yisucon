import mysql.connector
from mysql.connector.cursor import MySQLCursorPrepared


def connection():
    return mysql.connector.connect(
        user='root',
        password='',
        host='127.0.0.1',
        database='optomo',
        use_pure=True
    )


def get_friends(user):
    try:
        cnx = None
        cursor = None
        cnx = connection()
        cursor = cnx.cursor(prepared=True, cursor_class=MySQLCursorPrepared)

        stmt = "SELECT * FROM friends WHERE me = ?"
        cursor.execute(stmt, (user, ))
        rows = cursor.fetchall()
        if not rows:
            return None
        return rows[0][2].split(",")
    except mysql.connector.Error as err:
        print(err)
    finally:
        if cursor:
            cursor.close()
        if cnx:
            cnx.close()


def set_friends(friends, user):
    try:
        cnx = None
        cursor = None
        cnx = connection()
        cursor = cnx.cursor(prepared=True, cursor_class=MySQLCursorPrepared)

        stmt = "UPDATE friends SET friends = ? WHERE me = ?"
        cursor.execute(stmt, (','.join(friends), user))
        cnx.commit()
    except mysql.connector.Error as err:
        print(err)
    finally:
        if cursor:
            cursor.close()
        if cnx:
            cnx.close()
