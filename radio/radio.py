import requests
import os.path
from os.path import join
import hashlib
import re
import random
import time

from audio import Audio

RECORDINGS = "recordings"
UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
AUDIO_FILE_PATTERN = re.compile("^(" + UUID_PATTERN + ")/([^/]+)[0-9][0-9]\\.[\\w]+$")


class Radio:

    def __init__(self, audio: Audio):
        self.server_password = self.get_server_password()
        self.remote_host = "https://francoise.fm"
        # self.remote_host = "http://localhost:9090"
        self.all_files = []
        self.files_by_frequency = {}
        self.audio = audio

    @staticmethod
    def get_server_password():
        with open("server.properties", "r") as f:
            line = f.readline()
            if line.startswith("BASIC_AUTH_PASS="):
                return line[len("BASIC_AUTH_PASS="):].strip()
        raise Exception("Unable to find server password")

    def get_all_files(self):
        url = f"{self.remote_host}/audio/all"
        username = "Melville"
        rep = requests.get(url, auth=(username, self.server_password))
        print(rep.status_code)
        print(rep)
        return rep.json()

    def hash(self, file):
        block_size = 1024
        hasher = hashlib.md5()
        if not os.path.exists(file):
            return None
        with open(file, "rb") as f:
            buf = f.read(block_size)
            while len(buf) > 0:
                hasher.update(buf)
                buf = f.read(block_size)
        return hasher.hexdigest()

    def download_file(self, file):
        recording_file = join(RECORDINGS, file)
        url = f"{self.remote_host}/audio/" + file
        rep = requests.get(url)
        if rep.status_code == 200:
            print(f"Got file {file} ({len(rep.content)} bytes). Saving to {recording_file}")
            os.makedirs(os.path.dirname(recording_file), exist_ok=True)
            with open(recording_file, "wb") as f:
                f.write(rep.content)
            # Only necessary for mac os really
            self.audio.convert(recording_file)
        else:
            print(f"Error writing file: {rep}")

    @staticmethod
    def java_hash(str):
        h = 0
        for c in str:
            h = int((((31 * h + ord(c)) ^ 0x80000000) & 0xFFFFFFFF) - 0x80000000)
        return h

    def calculate_frequency(self, string_to_hash):
        return str((abs(self.java_hash(string_to_hash)) % 200 + 870) / 10)

    def calculate_frequencies(self):
        print("Calculating frequencies")
        for file in self.all_files:
            match = AUDIO_FILE_PATTERN.match(file["path"])
            if match:
                user_token = match.group(1)
                username = match.group(2)
                frequency = self.calculate_frequency(username + user_token)
                if frequency not in self.files_by_frequency:
                    self.files_by_frequency[frequency] = []
                self.files_by_frequency[frequency].append(file)
            else:
                print("Could not parse file: ", file)
        print(self.files_by_frequency)
        for freq in self.files_by_frequency.keys():
            print(freq, self.files_by_frequency[freq])

    def sync_files(self):
        self.all_files = self.get_all_files()
        print("Got all files: ", self.all_files)
        for file in self.all_files:
            recording_file = join(RECORDINGS, file["path"])
            # Check if we have already downloaded this file
            # if so, then check if the md5 hash matches
            print(f"Got {recording_file} local hash: {self.hash(recording_file)} remote hash: {file['hash']}")
            if not os.path.exists(recording_file) or self.hash(recording_file) != file['hash']:
                self.download_file(file["path"])
        self.calculate_frequencies()

    def play(self, freq):
        if freq in self.files_by_frequency:
            recording = random.choice(self.files_by_frequency[freq])
            recording_file = join(RECORDINGS, recording["path"])
            self.audio.play(recording_file)
        else:
            self.audio.playstatic()
            self.audio.playstatic()


audio = Audio()
radio = Radio(audio)
radio.sync_files()
radio.play("102.1")
time.sleep(2)
radio.play("104.7")
time.sleep(2)
radio.play("101.8")
time.sleep(10)

audio.stop()


