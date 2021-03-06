import mysql.connector
from mysql.connector.cursor import MySQLCursorPrepared
import re


def connection():
    return mysql.connector.connect(
        user='root',
        password='',
        host='127.0.0.1',
        database='optwitter',
        use_pure=True
    )


def initialize():
    try:
        cnx = None
        cursor = None
        cnx = connection()
        cursor = cnx.cursor(prepared=True, cursor_class=MySQLCursorPrepared)

        stmt = "DELETE FROM tweets WHERE id > 100000"
        cursor.execute(stmt)
        stmt = "DELETE FROM users WHERE id > 1000"
        cursor.execute(stmt)
        rows = cursor.fetchall()
        if not rows:
            return None
        return rows[0][1]
    except mysql.connector.Error as err:
        print(err)
    finally:
        if cursor:
            cursor.close()
        if cnx:
            cnx.close()


def insert(_id, text):
    try:
        cnx = None
        cursor = None
        cnx = connection()
        cursor = cnx.cursor(prepared=True, cursor_class=MySQLCursorPrepared)
        stmt = "INSERT INTO tweets (user_id, text, created_at) VALUES (?, ?, NOW())"
        cursor.execute(stmt, (_id, text))
        return
    except mysql.connector.Error as err:
        print(err)
    finally:
        if cursor:
            cursor.close()
        if cnx:
            cnx.close()


def find_user(name):
    try:
        cnx = None
        cursor = None
        cnx = connection()
        cursor = cnx.cursor(prepared=True, cursor_class=MySQLCursorPrepared)

        stmt = "SELECT * FROM users WHERE name = ?"
        cursor.execute(stmt, (name, ))
        rows = cursor.fetchall()
        if not rows:
            return None
        return rows[0]
    except mysql.connector.Error as err:
        print(err)
    finally:
        if cursor:
            cursor.close()
        if cnx:
            cnx.close()


def get_user_id(name):
    try:
        cnx = None
        cursor = None
        cnx = connection()
        cursor = cnx.cursor(prepared=True, cursor_class=MySQLCursorPrepared)

        stmt = "SELECT * FROM users WHERE name = ?"
        cursor.execute(stmt, (name, ))
        rows = cursor.fetchall()
        if not rows:
            return None
        return rows[0][0]
    except mysql.connector.Error as err:
        print(err)
    finally:
        if cursor:
            cursor.close()
        if cnx:
            cnx.close()


def get_user_name(_id):
    try:
        cnx = None
        cursor = None
        cnx = connection()
        cursor = cnx.cursor(prepared=True, cursor_class=MySQLCursorPrepared)

        stmt = "SELECT * FROM users WHERE id = ?"
        cursor.execute(stmt, (_id, ))
        rows = cursor.fetchall()
        if not rows:
            return None
        return rows[0][1]
    except mysql.connector.Error as err:
        print(err)
    finally:
        if cursor:
            cursor.close()
        if cnx:
            cnx.close()


def htmlify(text):
    if not text:
        return ''
    text = text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('\'', '&apos;').replace('"', '&quot;')
    return re.sub(r'#(\S+)(\s|$)', r'<a class="hashtag" href="/hashtag/\1">#\1</a>\2', text)


def get_user_tweets(user_id, until_time):
    try:
        cnx = None
        cursor = None
        cnx = connection()
        cursor = cnx.cursor(prepared=True, cursor_class=MySQLCursorPrepared)
        if until_time:
            stmt = "SELECT * FROM tweets WHERE user_id = ? AND created_at < ? ORDER BY created_at DESC"
            cursor.execute(stmt, (user_id, until_time))
        else:
            stmt = "SELECT * FROM tweets WHERE user_id = ? ORDER BY created_at DESC"
            cursor.execute(stmt, (user_id,))
        return cursor.fetchall()
    except mysql.connector.Error as err:
        print(err)
    finally:
        if cursor:
            cursor.close()
        if cnx:
            cnx.close()


def get_all_tweets(until_time):
    try:
        cnx = None
        cursor = None
        cnx = connection()
        cursor = cnx.cursor(prepared=True, cursor_class=MySQLCursorPrepared)
        if until_time:
            stmt = "SELECT * FROM tweets WHERE created_at < ? ORDER BY created_at DESC"
            cursor.execute(stmt, (until_time,))
        else:
            stmt = "SELECT * FROM tweets ORDER BY created_at DESC"
            cursor.execute(stmt)
        return cursor.fetchall()
    except mysql.connector.Error as err:
        print(err)
    finally:
        if cursor:
            cursor.close()
        if cnx:
            cnx.close()
