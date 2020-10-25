import logging
from aiortc import MediaStreamTrack

from server.channels import ServerEndpoint


class SpeechToTextAudioTrack(MediaStreamTrack):
    async def recv(self):
        # TODO
        pass


class SpeechToTextEndpoint(ServerEndpoint):
    async def send_msg(self, type, message):
        # TODO
        # TODO: CPU bound stuff must be moved to a separate process
        logging.info(f'SST server endpoint: {type} from {message["fromUser"]}')
