import asyncio
import logging
import firebase_admin
from firebase_admin import credentials, messaging

__all__ = ['push_incoming_call', 'push_invitation', 'push_invitation_answer', 'setup_notifications']


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


async def push_invitation(token, from_whom):
    payload = {'type': 'INVITATION', 'from_user': from_whom}
    await notify(token, payload)


async def push_invitation_answer(token, from_whom, positive):
    payload = {'type': 'INVITATION_ANSWER', 'from_user': from_whom, 'positive': positive}
    await notify(token, payload)


def setup_notifications(config):
    cred = credentials.Certificate(config.get('GOOGLE CREDENTIALS', 'PATH'))
    firebase_admin.initialize_app(cred)
