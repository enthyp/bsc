#!/usr/bin/env python
import argparse
import bcrypt
import psycopg2
from configparser import ConfigParser
from contextlib import contextmanager

i_query = 'INSERT INTO users(login, password) VALUES(%s, %s)'


@contextmanager
def get_connection():
    parser = ConfigParser()
    parser.read('config.ini')
    connection = psycopg2.connect(parser.get('DATABASE', 'URL'))
    try:
        yield connection
    finally:
        connection.close()


def add_user(login, password):
    with get_connection() as conn:
        with conn.cursor() as cur:
            hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt(12))
            cur.execute(i_query, (login, hashed.decode()))
            conn.commit()


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('login', default='user')
    parser.add_argument('password', default='password')
    return parser.parse_args()


if __name__ == '__main__':
    args = parse_args()
    add_user(args.login, args.password)

