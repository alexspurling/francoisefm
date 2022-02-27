from logging.handlers import RotatingFileHandler
import logging

import requests
import os.path
from os.path import join
import hashlib
import re
import random
import time

from analoguedial import AnalogueDial
from audio import Audio
from display import Display


log_formatter = logging.Formatter("[%(asctime)s] [%(levelname)s] %(message)s")

my_handler = RotatingFileHandler(filename="logs/radio.log", mode='a', maxBytes=10*1024*1024,
                                 backupCount=2, encoding=None, delay=False)
my_handler.setFormatter(log_formatter)
my_handler.setLevel(logging.DEBUG)

app_log = logging.getLogger()
app_log.setLevel(logging.DEBUG)
app_log.addHandler(my_handler)


RECORDINGS = "recordings"
UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
AUDIO_FILE_PATTERN = re.compile("^(" + UUID_PATTERN + ")/([^/]+)[0-9][0-9]\\.[\\w]+$")


class Radio:

    def __init__(self, audio, display):
        self.server_password = self.get_server_password()
        self.remote_host = "https://francoise.fm"
        # self.remote_host = "http://localhost:9090"
        self.all_files = []
        self.files_by_frequency = {}
        self.audio = audio
        self.display = display

    # load the file list into memory
    def init_files(self):
        for token in os.listdir(RECORDINGS):
            for file in os.listdir(join(RECORDINGS, token)):
                path = f"{token}/{file}"
                self.all_files.append({"path": path, "hash": self.hash(path)})
        self.calculate_frequencies()

    @staticmethod
    def get_server_password():
        with open("server.properties", "r") as f:
            line = f.readline()
            if line.startswith("BASIC_AUTH_PASS="):
                return line[len("BASIC_AUTH_PASS="):].strip()
        raise Exception("Unable to find server password")

    def get_all_files(self):
        url = f"{self.remote_host}/audio/radio"
        username = "Melville"
        rep = requests.get(url, auth=(username, self.server_password))
        logging.info(rep.status_code)
        logging.info(rep)
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
        url = f"{self.remote_host}/audio/radio/" + file
        rep = requests.get(url)
        if rep.status_code == 200:
            logging.info(f"Got file {file} ({len(rep.content)} bytes). Saving to {recording_file}")
            os.makedirs(os.path.dirname(recording_file), exist_ok=True)
            with open(recording_file, "wb") as f:
                f.write(rep.content)
        else:
            logging.info(f"Error writing file: {rep}")

    @staticmethod
    def java_hash(str):
        h = 0
        for c in str:
            h = int((((31 * h + ord(c)) ^ 0x80000000) & 0xFFFFFFFF) - 0x80000000)
        return h

    def calculate_frequency(self, string_to_hash):
        return abs(self.java_hash(string_to_hash)) % 200 + 870

    def calculate_frequencies(self):
        self.files_by_frequency = {}
        for file in self.all_files:
            match = AUDIO_FILE_PATTERN.match(file["path"])
            if match:
                user_token = match.group(1)
                name = match.group(2)
                # TODO get frequency from file name freq = match.group(3)
                frequency = self.calculate_frequency(name + user_token)
                if frequency not in self.files_by_frequency:
                    self.files_by_frequency[frequency] = []
                self.files_by_frequency[frequency].append(
                    {"file": os.path.join(RECORDINGS, file["path"]), "name": name})
        logging.info(f"Calculated {len(self.files_by_frequency)} frequencies")
        for freq in sorted(self.files_by_frequency.keys()):
            freq_str = f"{freq / 10}"
            files_for_freq = self.files_by_frequency[freq]
            num_files = len(files_for_freq)
            logging.info(f"Got {num_files} files for {freq_str} Mhz: {files_for_freq}")

    def sync_files(self):
        self.all_files = self.get_all_files()
        logging.info(f"Got {len(self.all_files)} files")
        for file in self.all_files:
            recording_file = join(RECORDINGS, file["path"])
            # Check if we have already downloaded this file
            # if so, then check if the md5 hash matches
            if not os.path.exists(recording_file) or self.hash(recording_file) != file['hash']:
                logging.info(f"Got new file {recording_file} local hash: {self.hash(recording_file)} remote hash: {file['hash']}")
                self.download_file(file["path"])
        self.calculate_frequencies()

    def play(self, freq, target_freq):
        logging.info(f"Tuning to {freq} (target: {target_freq})")
        freq_files = self.files_by_frequency[target_freq]
        random_index = random.randrange(0, len(freq_files))
        display.set_freq(freq)
        if freq == target_freq:
            display.display_station(freq_files[random_index]["name"])
            self.audio.play_track(freq_files, random_index)
        else:
            display.display_station(None)
            self.audio.play_nearby(freq_files, random_index)

    def tune(self, freq):
        if freq in self.files_by_frequency:
            self.play(freq, freq)
        elif (freq + 1) in self.files_by_frequency:
            self.play(freq, freq + 1)
        elif (freq - 1) in self.files_by_frequency:
            self.play(freq, freq - 1)
        else:
            display.set_freq(freq)
            display.display_station(None)
            self.audio.play_static()


display = Display()
audio = Audio(display)
radio = Radio(audio, display)

dial = AnalogueDial()
# dial.loop()

# Original frequency dial based on digital rotary dial
# def frequency_changed(freq):
#     radio.tune(freq)
#
# FrequencyDial(frequency_changed)
#
# frequency_changed(1000)


try:
    # First load files from the file system
    radio.init_files()
    # Then try to do an initial sync but it will fail if we have no internet
    radio.sync_files()
except Exception as e:
    logging.exception(f"Error! {e}", exc_info=e)

freq = 1

while True:
    try:
        for i in range(0, 300):
            for j in range(0, 10):
                new_freq, _ = dial.get_freq()
                if new_freq != freq:
                    freq = new_freq
                    radio.tune(new_freq)

                time.sleep(1 / 10)

            # Periodically check for the next track to play
            audio.check_next_track()

        # Sync files every 300 seconds
        radio.sync_files()
    except Exception as e:
        logging.exception(f"Error! {e}", exc_info=e)
        logging.error("Sleeping for 10s before trying again")
        time.sleep(10)
