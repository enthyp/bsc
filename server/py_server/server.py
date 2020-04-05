import logging
import aiohttp
import sys
import uuid
from aiohttp import web

from notifications import notify, setup_notifications

logging.basicConfig(stream=sys.stdout, level=logging.DEBUG)


async def websocket_handler(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    this = str(uuid.uuid4())
    clients = request.app['clients']
    clients[this] = ws

    try:
        async for msg in ws:
            logging.debug(msg.data)

            if msg.type == aiohttp.WSMsgType.TEXT:
                for c_id, c_ws in request.app['clients'].items():
                    if c_id != this:
                        logging.debug('SENT: {} -> {}'.format(this, c_id))
                        await c_ws.send_str(msg.data)
            elif msg.type == aiohttp.WSMsgType.ERROR:
                logging.debug('ws connection closed with exception {}'.format(ws.exception()))
                await ws.close()
                break
            elif msg.type == aiohttp.WSMsgType.CLOSE:
                logging.debug('closing')
                await ws.close()
                break
    except Exception as e:
        logging.debug(e)
    finally:
        logging.debug('UGH')

    logging.debug('websocket connection closed')
    del clients[this]

    return ws


async def token_handler(request):
    body = await request.json()
    identity, token = body['id'], body['token']
    request.app['tokens'][identity] = token

    # Let's try to push a notification to him.
    await notify(token, {'type': 'incoming call', 'caller': 'bob'})

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
