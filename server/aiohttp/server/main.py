import configparser
import json
import logging
import aiohttp
import sys
from aiohttp import web
from aiohttp_security import (
    remember, forget, check_authorized,
    authorized_userid
)

from server.auth import check_credentials, setup_auth
from server.notifications import push_invitation, setup_notifications
from server.call import ClientEndpoint, setup_server
from server.storage import setup_db

logging.basicConfig(stream=sys.stdout, level=logging.DEBUG)
routes = web.RouteTableDef()


@routes.get('/')
async def websocket_handler(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    logging.info('Connected...')

    server = request.app['server']
    # TODO: nick and token should be in place (login)
    endpoint = ClientEndpoint(socket=ws, server=server)

    try:
        async for msg in ws:
            if msg.type == aiohttp.WSMsgType.TEXT:
                msg_str = await msg.json()
                msg_obj = json.loads(msg_str)
                msg_type = msg_obj['type']
                payload = json.loads(msg_obj['payload'])
                await endpoint.dispatch(msg_type, payload)

            elif msg.type == aiohttp.WSMsgType.CLOSED:
                logging.info('WebSocket connection closing...')
                await ws.close()
                break

            elif msg.type == aiohttp.WSMsgType.ERROR:
                logging.error('WebSocket connection closed with exception {}'.format(ws.exception()))
                await ws.close()
                break

    except Exception as e:
        # TODO: some global error handler? (CancelledError...)
        logging.error((e, type(e)))
    finally:
        logging.info('Websocket connection closed')

    server.rm_client(client=endpoint)  # TODO: conversation cleanup? maybe when all parties leave...
    return ws


@routes.post('/token')
async def token_handler(request):
    await check_authorized(request)
    login = await authorized_userid(request)

    body = await request.json()
    # TODO: DB only?
    await request.app['server'].on_token(login, body['token'])

    return web.Response()


@routes.post('/login')
async def handle_login(request):
    body = await request.json()
    try:
        login, pwd = body['login'], body['password']
    except KeyError:
        raise web.HTTPBadRequest()

    logging.info("LOGIN: {}".format(login))
    storage = request.app['storage']
    if await check_credentials(storage, login, pwd):
        response = web.HTTPOk()
        await remember(request, response, login)
        raise response
    else:
        raise web.HTTPUnauthorized()


@routes.get('/users/search')
async def handle_search(request):
    await check_authorized(request)
    login = await authorized_userid(request)

    query = request.rel_url.query['query']
    logging.info("SEARCH: {} from {}".format(query, login))

    storage = request.app['storage']
    users = await storage.find_users(query)

    data = [{'login': user[0]} for user in users]
    return web.json_response(data)


@routes.post('/users/invite')
async def handle_invitation(request):
    await check_authorized(request)
    login = await authorized_userid(request)

    user = await request.json()
    logging.info("INVITE: {} invites {}".format(login, user['login']))

    storage = request.app['storage']

    # TODO: fewer DB calls? locking?
    if not await storage.registered(user['login']):
        return web.HTTPBadRequest()

    if user['login'] == login:
        return web.HTTPBadRequest()

    if user['login'] in await storage.get_friends(login):
        return web.HTTPBadRequest()

    if user['login'] in await storage.get_invitations(login):
        return web.HTTPBadRequest()

    token = await storage.get_token(user['login'])
    await push_invitation(token, login)

    await storage.add_invitation(login, user['login'])

    return web.Response()


@routes.post('/logout')
async def handle_logout(request):
    logging.info("LOGOUT")
    response = web.HTTPOk()
    await forget(request, response)
    raise response


def main():
    parser = configparser.ConfigParser()
    parser.read('../config.ini')

    app = web.Application()

    setup_db(app, parser)
    setup_notifications(parser)
    setup_auth(app)
    setup_server(app)

    app.add_routes(routes)
    web.run_app(app, host='192.168.100.106', port=5000)


if __name__ == '__main__':
    main()
