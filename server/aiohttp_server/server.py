import json
import logging
import uuid
from notifications import async_notify


class Server:
    def __init__(self):
        self.clients = {}
        self.tokens = {}  # TODO: DB
        self.calls = {}

    def add_client(self, client):
        if client.nick not in self.clients:
            self.clients[client.nick] = client
        else:
            # TODO: handle error (possible?)
            logging.error(f'Duplicate user nick: {client.nick}')

    def rm_client(self, client):
        if client.nick in self.clients:
            del self.clients[client.nick]
        else:
            # TODO: handle error (possible?)
            logging.error(f'No user to delete: {client.nick}')

    # TODO: property getters?
    def get_endpoint(self, nick):
        # TODO: handle error
        return self.clients.get(nick, None)

    def get_token(self, nick):
        # TODO: handle error
        return self.tokens.get(nick, None)

    def get_call(self, call_id):
        # TODO: handle error
        return self.calls.get(call_id, None)

    def on_token(self, identity, token):
        self.tokens[identity] = token  # TODO: DB
        logging.info(f'Token received for user {identity}')

    async def initiate_call(self, caller, callee):
        token = self.tokens.get(callee, None)
        if token:
            conversation = Conversation()
            call_id = str(uuid.uuid4())  # TODO: tb replaced by DB id (quality rating, duration etc.)
            self.calls[call_id] = conversation
            await async_notify(token, {'type': ClientEndpoint.INCOMING, 'caller': caller, 'call_id': call_id})
            return conversation


class Conversation:
    def __init__(self):
        self.parties = {}

    def join(self, client):
        self.parties[client.nick] = client

    def leave(self, client):
        del self.parties[client.nick]

    async def signal(self, sender, type, msg):
        for nick, endpoint in self.parties.items():
            if nick != sender.nick:
                await endpoint.send_msg(type, msg)


# TODO: states seem to deserve their own objects to clean it all up
class ClientEndpoint:

    # Client connection state
    INIT = 'INIT'
    LOGGED_IN = 'LOGGED IN'
    RENDEZVOUS = 'RENDEZVOUS'
    SIGNALLING = 'SIGNALLING'

    # Messages from the client
    LOGIN = 'LOGIN'
    CALL = 'CALL'
    ACCEPT = 'ACCEPT'
    REFUSE = 'REFUSE'

    # Messages to the client
    INCOMING = 'INCOMING'  # Firebase only!
    ACCEPTED = 'ACCEPTED'
    REFUSED = 'REFUSED'

    # From/to
    OFFER = 'OFFER'
    ANSWER = 'ANSWER'
    ICE = 'ICE_CANDIDATE'

    def __init__(self, socket, server):
        self.socket = socket
        self.server = server
        self.state = ClientEndpoint.INIT
        self.nick = None  # TODO: should come from login process
        self.token = None
        self.conversation = None

        # Message handlers for states
        self.handlers = {
            (ClientEndpoint.INIT, ClientEndpoint.LOGIN): self.login,
            (ClientEndpoint.LOGGED_IN, ClientEndpoint.CALL): self.call,
            (ClientEndpoint.LOGGED_IN, ClientEndpoint.ACCEPT): self.accept,
            (ClientEndpoint.LOGGED_IN, ClientEndpoint.REFUSE): self.refuse,
            (ClientEndpoint.SIGNALLING, ClientEndpoint.OFFER): self.offer,
            (ClientEndpoint.SIGNALLING, ClientEndpoint.ANSWER): self.answer,
            (ClientEndpoint.SIGNALLING, ClientEndpoint.ICE): self.ice,
        }

    async def dispatch(self, type, msg):
        handler = self.handlers.get((self.state, type), None)
        if handler:
            await handler(msg)  # TODO: handle errors
        else:
            logging.error(f'No handler found for {type} in state {self.state}')

    async def login(self, msg):
        nick = msg['nick']
        token = self.server.get_token(nick)
        if not token:
            logging.error(f'No token on login for user: {nick}')
            return

        self.token = token
        self.nick = nick
        self.state = ClientEndpoint.LOGGED_IN
        self.server.add_client(self)
        logging.info(f'Logged in: {nick}')

    async def call(self, msg):
        callee = msg['to']

        self.conversation = await self.server.initiate_call(self.nick, callee)
        if self.conversation:
            self.state = ClientEndpoint.RENDEZVOUS
            logging.info(f'Incoming call pushed from {self.nick} to {callee}')
        else:
            logging.error(f'Incoming call from {self.nick} to {callee}: no token for {callee}')

    async def accept(self, msg):
        caller = msg['to']
        call_id = msg['call_id']
        self.conversation = self.server.get_call(call_id)

        if not self.conversation:
            # TODO: handle errors
            logging.error(f'Accept: call {call_id} not found')
            return

        caller_endpoint = self.server.get_endpoint(caller)
        if not caller_endpoint:
            # TODO: handle errors
            logging.error(f'Accept: user {caller} not found')
            return

        self.conversation.join(self)
        self.state = ClientEndpoint.SIGNALLING
        await caller_endpoint.on_accepted_call(self.nick)

    async def refuse(self, msg):
        caller = msg['to']
        call_id = msg['call_id']
        self.conversation = self.server.get_call(call_id)

        if not self.conversation:
            # TODO: handle errors
            logging.error(f'Accept: call {call_id} not found')
            return

        caller_endpoint = self.server.get_endpoint(caller)
        if not caller_endpoint:
            # TODO: handle errors
            logging.error(f'Refuse: user {caller} not found')
        else:
            self.conversation = None
            self.state = ClientEndpoint.LOGGED_IN
            caller_endpoint.on_refused_call(self.nick)

    async def offer(self, msg):
        await self.conversation.signal(self, ClientEndpoint.OFFER, msg)
        logging.info(f'Offer published by {self.nick}: {msg}')

    async def answer(self, msg):
        await self.conversation.signal(self, ClientEndpoint.ANSWER, msg)
        logging.info(f'Answer published by {self.nick}: {msg}')

    async def ice(self, msg):
        await self.conversation.signal(self, ClientEndpoint.ICE, msg)
        logging.info(f'ICE candidate published by {self.nick}: {msg}')

    async def on_accepted_call(self, callee):
        if self.state != ClientEndpoint.RENDEZVOUS:
            logging.error(f'on_accepted_call from {callee} to {self.nick} in state {self.state}')
            return

        self.conversation.join(self)
        self.state = ClientEndpoint.SIGNALLING
        await self.send_msg(ClientEndpoint.ACCEPTED, {'from': self.nick, 'to': callee})
        logging.info(f'Accepted call pushed to: {self.nick}')

    async def on_refused_call(self, callee):
        if self.state != ClientEndpoint.RENDEZVOUS:
            logging.error(f'on_refused_call from {callee} to {self.nick} in state {self.state}')
            return

        self.conversation = None
        self.state = ClientEndpoint.LOGGED_IN
        await self.send_msg(ClientEndpoint.REFUSED, {'from': self.nick, 'to': callee})
        logging.info(f'Refused call pushed to: {self.nick}')

    async def send_msg(self, type, payload):
        payload_json = json.dumps(payload)
        ws_msg = json.dumps({'type': type, 'payload': payload_json})
        await self.socket.send_json(ws_msg)