import requests
import os.path
from os.path import join
import hashlib
import re
import random
import time

from audio import Audio
from display import Display
from encoder import Encoder
from frequencydial import FrequencyDial

RECORDINGS = "recordings"
UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
AUDIO_FILE_PATTERN = re.compile("^(" + UUID_PATTERN + ")/([^/]+)[0-9][0-9]\\.[\\w]+$")


class Radio:

    def __init__(self, audio: Audio):
        self.server_password = self.get_server_password()
        # self.remote_host = "https://francoise.fm"
        self.remote_host = "http://localhost:9090"
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
        else:
            print(f"Error writing file: {rep}")

    @staticmethod
    def java_hash(str):
        h = 0
        for c in str:
            h = int((((31 * h + ord(c)) ^ 0x80000000) & 0xFFFFFFFF) - 0x80000000)
        return h

    def calculate_frequency(self, string_to_hash):
        return abs(self.java_hash(string_to_hash)) % 200 + 870

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
                self.files_by_frequency[frequency].append(os.path.join(RECORDINGS, file["path"]))
            # else:
            #     print("Could not parse file: ", file)
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

    def tune(self, freq):
        print(f"Tuning to {freq}")
        if freq in self.files_by_frequency:
            freq_files = self.files_by_frequency[freq]
            random_index = random.randrange(0, len(freq_files))
            self.audio.play_track(freq_files, random_index)
        elif (freq + 1) in self.files_by_frequency:
            freq_files = self.files_by_frequency[freq + 1]
            random_index = random.randrange(0, len(freq_files))
            self.audio.play_nearby(freq_files, random_index)
        elif (freq - 1) in self.files_by_frequency:
            freq_files = self.files_by_frequency[freq - 1]
            random_index = random.randrange(0, len(freq_files))
            self.audio.play_nearby(freq_files, random_index)
        else:
            self.audio.play_static()


audio = Audio()
radio = Radio(audio)
radio.sync_files()

display = Display()


def frequency_changed(freq):
    print(f"Frequency: {freq}")
    display.display_station(freq, "Alex")
    radio.tune(freq)


FrequencyDial(frequency_changed)


while True:
    print("Sleepy time")
    time.sleep(10)

# radio.tune(1021)
# time.sleep(3)
# radio.tune(1047)
# time.sleep(3)
# radio.tune(1034)
# time.sleep(2)
# radio.tune(1035)
# time.sleep(5)
# # for i in range(0, 20):
# #     time.sleep(0.5)
# #     audio.check_next_track()
# radio.tune(1047)
# time.sleep(10)

# audio.stop()

# from frequency import Frequency
# def frequency_changed(freq):
#     print("New frequency:", freq)
#
# freq = Frequency(frequency_changed)
#
#
# while True:
#     print("Current frequency:", freq.get_frequency())
#     time.sleep(5)

