from abc import ABC
import json
import logging
from typing import Optional

from server.storage import DBStorage


# LESSONS (kind of reinventing the wheel here):
#  - precisely define requirements using User Stories and Actors
#  - (simultaneously) figure out potential entities in the system,
#    like Channels, ClientEndpoints, Manager etc. and translate
#    User Stories into terms of their interactions. At this point
#    you plot finite state machines, flow control diagrams and the
#    like
#  - create well defined and granular issues from these interactions
#  - before implementation, consider responsibilities of interacting
#    objects, then what state they need to keep to do their job etc.


# Responsible for:
#  - creating Channel instances and keeping them in memory
#  - connecting incoming clients with requested channels
#  - cleaning up the resources once all users leave the Channel
class ChannelManager:
    def __init__(self, storage, server_endpoints):
        self.storage = storage
        self.server_endpoints = server_endpoints
        self.channels = {}

    def join(self, endpoint, channel_id):
        channel = self.channels.get(channel_id, None)

        if not channel:
            channel = Channel(channel_id, self, self.server_endpoints)
            self.channels[channel_id] = channel
        channel.join(endpoint)

    def on_channel_empty(self, channel_id):
        del self.channels[channel_id]


# Responsible for:
#  - keeping ClientEndpoint instances in memory
#  - sending addressed messages between appropriate ClientEndpoints
#  - holding state concerning recording policy - if a Channel allows
#    recording, it also keeps instances of ClientEndpoints that only
#    record and are not active users - hence don't leave themselves,
#    but are triggered to leave by the Channel
#  - triggering manager callback once all users leave
class Channel:
    def __init__(self, channel_id, manager, server_endpoints):
        self.id = channel_id
        self.manager = manager
        self.min_endpoints = len(server_endpoints)
        self.participants = {e.nick: e for i, e in enumerate(server_endpoints)}
        for e in server_endpoints:
            e.join(self)

    @property
    def empty(self) -> bool:
        return len(self.participants) == self.min_endpoints

    @staticmethod
    def user_nick(nick: str) -> str:
        return f'user-{nick}'

    @property
    def online_users(self):
        return list(self.participants.keys())

    def join(self, endpoint):
        for p in self.participants:
            self.route(ClientEndpoint.JOINED, {'who': endpoint.nick, 'to_user': p.nick}, '')
        self.participants[self.user_nick(endpoint.nick)] = endpoint

    def leave(self, endpoint):
        del self.participants[self.user_nick(endpoint.nick)]

        if self.empty:
            del self.participants  # TODO: circular dependency between Channel and ServerEndpoints???
            self.manager.on_channel_empty(self.id)
        else:
            for p in self.participants:
                self.route(ClientEndpoint.LEFT, {'who': endpoint.nick, 'to_user': p.nick}, '')

    def route(self, type, message, sender, user=True):
        recipient = message.get('toUser', None)
        endpoint = self.participants.get(recipient, None)
        if endpoint:
            del message['toUser']
            message['fromUser'] = self.user_nick(sender) if user else sender
            endpoint.send_msg(type, message)
        else:
            logging.error(f'In channel {self.id}: recipient {recipient} absent')


# Responsible for:
#  - receiving messages addressed to it
#  - processing these messages (and others - WebRTC) in arbitrary non-CPU bound way
class ServerEndpoint(ABC):
    def __init__(self, nick):
        self.nick = nick
        self.channel = None

    def join(self, channel):
        self.channel = channel

    def route_msg(self, type, message):
        self.channel.route(type, message, self.nick, user=False)

    def send_msg(self, type, message):
        raise NotImplementedError


# TODO: states seem to deserve their own objects to clean it all up
class ClientEndpoint:

    # Client connection state
    INIT = 'INIT'
    ONLINE = 'ONLINE'

    # Messages from the client
    JOIN = 'JOIN'
    LEAVE = 'LEAVE'

    # Messages to the client
    ACCEPTED = 'ACCEPTED'
    REFUSED = 'REFUSED'
    JOINED = 'JOINED'
    LEFT = 'LEFT'

    # From/to
    OFFER = 'OFFER'
    ANSWER = 'ANSWER'
    ICE = 'ICE_CANDIDATE'

    def __init__(self, nick, socket, channel_manager: ChannelManager, storage: DBStorage):
        self.nick = nick
        self.socket = socket
        self.channel_manager = channel_manager
        self.storage = storage
        self.state = ClientEndpoint.INIT
        self.channel: Optional[Channel] = None

        # Message handlers for states
        self.handlers = {
            (ClientEndpoint.INIT, ClientEndpoint.JOIN): self.join,
            (ClientEndpoint.ONLINE, ClientEndpoint.OFFER): self.offer,
            (ClientEndpoint.ONLINE, ClientEndpoint.ANSWER): self.answer,
            (ClientEndpoint.ONLINE, ClientEndpoint.ICE): self.ice,
            (ClientEndpoint.ONLINE, ClientEndpoint.LEAVE): self.leave,
        }

    async def dispatch(self, type, msg):
        handler = self.handlers.get((self.state, type), None)
        if handler:
            await handler(msg)  # TODO: handle errors
        else:
            logging.error(f'No handler found for {type} in state {self.state}')

    async def join(self, msg):
        channel_id = msg['channelId']
        members = await self.storage.get_channel_members(channel_id)

        if self.nick not in members:
            # TODO: drop connection actually
            await self.send_msg(ClientEndpoint.REFUSED, {})
            logging.warning(f'Unauthorized attempt to join channel {channel_id} by user {self.nick}')
            return

        self.channel_manager.join(self, channel_id)
        self.state = ClientEndpoint.ONLINE
        logging.info(f'User {self.nick} joined channel {channel_id}')

        await self.send_msg(ClientEndpoint.ACCEPTED, {'online': self.channel.online_users})

    async def leave(self, msg):
        self.channel.leave(self)
        logging.info(f'User {self.nick} left channel {self.channel.id}')
        # TODO: garbage collected or not?

    async def offer(self, msg):
        await self.channel.route(ClientEndpoint.OFFER, msg, self.nick)
        # TODO: no need to print it
        from pprint import pprint
        pprint(msg)
        logging.info(f'Offer published by {self.nick}: {msg}')

    async def answer(self, msg):
        await self.channel.route(ClientEndpoint.ANSWER, msg, self.nick)
        logging.info(f'Answer published by {self.nick}: {msg}')

    async def ice(self, msg):
        await self.channel.route(ClientEndpoint.ICE, msg, self.nick)
        logging.info(f'ICE candidate published by {self.nick}: {msg}')

    async def send_msg(self, type, payload):
        ws_msg = json.dumps({'type': type, 'payload': payload})
        await self.socket.send_json(ws_msg)


def setup_server(app, server_endpoints):
    async def _setup(app):
        manager = ChannelManager(app['storage'], server_endpoints)
        app['channel manager'] = manager

    app.on_startup.append(_setup)
