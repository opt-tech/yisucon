import mysql.connector
import re


def connection():
    return mysql.connector.connect(
            user='root',
            password='',
            host='127.0.0.1',
            database='isuwitter'
        )


def find_user(name):
    try:
        cnx = connection()
        cursor = cnx.cursor(prepared=True)

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
        cnx = connection()
        cursor = cnx.cursor(prepared=True)

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
        cnx = connection()
        cursor = cnx.cursor(prepared=True)

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
        cnx = connection()
        cursor = cnx.cursor(prepared=True)
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
        cnx = connection()
        cursor = cnx.cursor(prepared=True)
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
