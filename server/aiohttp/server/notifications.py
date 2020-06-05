import asyncio
import logging
import firebase_admin
from firebase_admin import credentials, messaging


async def notify(token, payload):
    message = messaging.Message(
        data=payload,
        token=token
    )

    loop = asyncio.get_running_loop()
    await loop.run_in_executor(
       None, lambda: messaging.send(message)
    )
    logging.info('Notified!')


async def push_incoming_call(token, caller, call_id):
    payload = {'type': 'INCOMING', 'caller': caller, 'call_id': call_id}
    await notify(token, payload)


# TODO
async def push_invitation(token, from_whom):
    pass


def setup_notifications(config):
    cred = credentials.Certificate(config.get('GOOGLE CREDENTIALS', 'PATH'))
    firebase_admin.initialize_app(cred)
