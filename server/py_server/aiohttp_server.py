import json
import logging
import aiohttp
import sys
from aiohttp import web
from enum import Enum

from notifications import async_notify, setup_notifications

logging.basicConfig(stream=sys.stdout, level=logging.DEBUG)


class ClientState(Enum):
    INIT = 0
    LOGGED_IN = 1


class ClientHandler:
    def __init__(self, nick, state, socket):
        self.nick = nick
        self.state = state
        self.socket = socket

    @property
    def logged_in(self):
        return self.state == ClientState.LOGGED_IN

    def log_in(self, nick):
        self.nick = nick
        self.state = ClientState.LOGGED_IN


async def send_msg(type, payload, socket):
    p_json = json.dumps(payload)
    ws_msg = json.dumps({'type': type, 'payload': p_json})
    await socket.send_json(ws_msg)


async def websocket_handler(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    clients = request.app['clients']
    handler = ClientHandler(nick=None, state=ClientState.INIT, socket=ws)

    async def handle_login(handler, nickname):
        logging.debug(f'login {nickname}')
        clients[nickname] = handler
        handler.log_in(nickname)

    async def handle_call(caller, callee, tokens):
        if callee in tokens:
            token = tokens[callee]
            await async_notify(token, {'type': 'incoming', 'caller': caller})
            logging.debug('sent incoming')
        else:
            logging.debug('call failed')
            # TODO: inform about error

    async def handle_accept(caller, callee, clients):
        if caller in clients:
            client = clients[caller]
            await send_msg('ACCEPTED', {'from': caller, 'to': callee}, client.socket)
            logging.debug('sent accepted')
        else:
            logging.debug('accept failed')
            # TODO: inform about error

    logging.debug('Connected...')
    try:
        async for msg in ws:
            if msg.type == aiohttp.WSMsgType.TEXT:
                msg_data = msg.json()

                # TODO: dispatch method
                logging.debug(msg_data)
                if not handler.logged_in:
                    await handle_login(handler, msg_data)
                    continue

                msg_obj = json.loads(msg_data)
                msg_type = msg_obj['type']
                payload = json.loads(msg_obj['payload'])

                if msg_type == 'CALL':
                    await handle_call(payload['from'], payload['to'], request.app['tokens'])
                elif msg_type == 'ACCEPT':
                    await handle_accept(payload['from'], payload['to'], request.app['clients'])
                else:
                    if msg_type in {'OFFER', 'ANSWER', 'ICE_CANDIDATE'}:
                        for nick, other_handler in request.app['clients'].items():
                            if nick != handler.nick:
                                logging.debug(f'SENT: {handler.nick} -> {nick} : {msg_data}')
                                await other_handler.socket.send_json(msg_data)
                    else:
                        logging.error(f'Unknown msg: {msg.type}')

            elif msg.type == aiohttp.WSMsgType.ERROR:
                logging.debug('ws connection closed with exception {}'.format(ws.exception()))
                await ws.close()
                break
            elif msg.type == aiohttp.WSMsgType.CLOSED:
                logging.debug('closing')
                await ws.close()
                break
    except Exception as e:
        logging.debug(e)
    finally:
        logging.debug('websocket connection closed')

    del clients[handler.nick]
    return ws


async def token_handler(request):
    body = await request.json()
    identity, token = body['id'], body['token']
    request.app['tokens'][identity] = token
    logging.info(f'got token from {identity}')

    return web.Response()


def main():
    setup_notifications()

    app = web.Application()
    app['clients'] = {}
    app['tokens'] = {}
    app.add_routes([
        web.get('/', websocket_handler),
        web.post('/token', token_handler)
    ])

    web.run_app(app, host='192.168.100.106', port=5000)


if __name__ == '__main__':
    main()
