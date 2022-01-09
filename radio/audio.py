import subprocess
import os.path
from shutil import which

class Audio:

    def __init__(self):
        self.staticfile = "static.wav"
        self.process = None

    def convert(self, file: str):
        mp3_file = file + ".mp3"

        print(f"Converting {file} to mp3")
        args = ["ffmpeg", "-y", "-loglevel", "warning", "-i", file, mp3_file]
        result = subprocess.call(args, stdin=None, stdout=None, stderr=None, shell=False, timeout=None)
        print(f"ffmpeg result: {result}")

    def play(self, file: str, loop=False):
        if file.endswith(".webm") or file.endswith(".ogg"):
            file = file + ".mp3"

        self.stop()

        if os.path.exists("/usr/bin/omxplayer.bin"):
            print(f"Playing {file} with omxplayer")
            self.process = subprocess.Popen(["/usr/bin/omxplayer.bin", "-o", "local", '--loop' if loop else '', file], shell=False)
            print("created process", self.process)
            print("created process", self.process.pid)
        else:
            # Ok, try afplay
            print(f"Playing {file} with afplay")
            self.process = subprocess.Popen(["/usr/bin/afplay", file], shell=False)
            print("Process: ", self.process.pid)

    def stop(self):
        if self.process is not None:
            try:
                print("Trying to terminate process", self.process)
                print("Trying to terminate process", self.process.pid)
                self.process.terminate()
                print("Return code", self.process.returncode)
            except Exception as e:
                print("Failed to terminate previous process", e)

    def playstatic(self):
        self.play(self.staticfile, loop=True)