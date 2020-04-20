import asyncio
import configparser
import logging
import firebase_admin
from firebase_admin import credentials, messaging


async def async_notify(token, payload):
    message = messaging.Message(
        data=payload,
        token=token
    )

    loop = asyncio.get_running_loop()
    await loop.run_in_executor(
       None, lambda: messaging.send(message)
    )
    logging.info('Notified!')


def notify(token, payload):
    message = messaging.Message(
        data=payload,
        token=token
    )
    messaging.send(message)


def setup_notifications():
    parser = configparser.ConfigParser()
    parser.read('config.ini')
    cred = credentials.Certificate(parser.get('GOOGLE CREDENTIALS', 'PATH'))
    firebase_admin.initialize_app(cred)
