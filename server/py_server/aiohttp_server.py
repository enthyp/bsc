import json
import logging
import aiohttp
import asyncio
import sys
import uuid
from aiohttp import web

from notifications import async_notify, setup_notifications

logging.basicConfig(stream=sys.stdout, level=logging.DEBUG)


async def websocket_handler(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    this = str(uuid.uuid4())
    clients = request.app['clients']
    clients[this] = ws
    # await ws.send_json({'type': 'bob', 'payload': 'uncle'})

    async def handle_call(caller, callee, tokens):
        if callee in tokens:
            token = tokens[callee]
            await async_notify(token, {'type': 'incoming', 'caller': caller})
        else:
            logging.debug('FUCK!')
            # TODO: inform about error

    async def handle_accept(caller, callee, clients):
        if caller in clients:
            client = clients[callee]
            client.send_str(json.dumps({'type': 'accept', 'from': callee, 'to': caller}))
        else:
            logging.debug('ACCEPT FUCK!')
            # TODO: inform about error

    logging.debug('Connected...')
    try:
        async for msg in ws:
            if msg.type == aiohttp.WSMsgType.TEXT:
                # TODO: dispatch
                msg = json.loads(msg.json())
                msg_type = msg['type']
                payload = json.loads(msg['payload'])

                if msg_type == 'CALL':
                    await handle_call(payload['from'], payload['to'], request.app['tokens'])
                elif msg_type == 'ACCEPT':
                    await handle_accept(payload['from'], payload['to'], request.app['clients'])
                else:
                    if msg_type in {'OFFER', 'ANSWER', 'ICE_CANDIDATE'}:
                        for c_id, c_ws in request.app['clients'].items():
                            if c_id != this:
                                logging.debug('SENT: {} -> {}'.format(this, c_id))
                                await c_ws.send_str(msg.data)
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

    del clients[this]

    return ws


async def token_handler(request):
    body = await request.json()
    identity, token = body['id'], body['token']
    request.app['tokens'][identity] = token

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
