from aiortc import MediaStreamTrack

from server.channels import ServerEndpoint


class SpeechToTextAudioTrack(MediaStreamTrack):
    async def recv(self):
        # TODO
        pass


class SpeechToTextEndpoint(ServerEndpoint):
    def send_msg(self, type, message):
        # TODO
        # TODO: CPU bound stuff must be moved to a separate process
        pass
