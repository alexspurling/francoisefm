import logging
import requests
import hashlib
import os

from os.path import join
import json

RECORDINGS = "recordings"
STATIONS = "stations.json"


class Sync:

    def __init__(self):
        self.server_password = self.get_server_password()
        self.remote_host = "https://francoise.fm"
        # self.remote_host = "http://localhost:9090"
        self.all_stations = []
        self.files_by_frequency = dict()

    @staticmethod
    def get_server_password():
        with open("server.properties", "r") as f:
            line = f.readline()
            if line.startswith("BASIC_AUTH_PASS="):
                return line[len("BASIC_AUTH_PASS="):].strip()
        raise Exception("Unable to find server password")

    def _update_stations(self, stations):
        self.all_stations = stations
        self.calculate_files_by_frequency()
        self.save_local_stations()

    def calculate_files_by_frequency(self):
        for station in self.all_stations:
            freq = station['frequency']
            freq_files = self.files_by_frequency.get(freq)
            if freq_files is None:
                freq_files = []
                self.files_by_frequency[freq] = freq_files
            freq_files.extend(station['files'])

    def get_local_stations(self):
        if os.path.exists(STATIONS):
            with open(STATIONS, "r") as f:
                local_stations = json.load(f)
            self._update_stations(local_stations)

    def save_local_stations(self):
        with open(STATIONS, "w") as f:
            json.dump(self.all_stations, f, indent=2)

    def get_remote_stations(self):
        url = f"{self.remote_host}/audio/radio"
        username = "Melville"
        rep = requests.get(url, auth=(username, self.server_password))
        logging.info(rep.status_code)
        logging.info(rep)
        self._update_stations(rep.json())

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

    def sync_files(self):
        # Load any previously saved stations from disk
        self.get_local_stations()
        try:
            self.get_remote_stations()
        except Exception as e:
            logging.exception(f"Error! {e}", exc_info=e)
            logging.error("Error syncing with server")

        logging.info(f"Got {len(self.all_stations)} stations")
        for station in self.all_stations:
            for file in station['files']:
                recording_file = join(RECORDINGS, file["path"])
                # Check if we have already downloaded this file
                # if so, then check if the md5 hash matches
                if not os.path.exists(recording_file) or self.hash(recording_file) != file['hash']:
                    logging.info(f"Got new file {recording_file} local hash: {self.hash(recording_file)} remote hash: {file['hash']}")
                    self.download_file(file["path"])

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


if __name__ == "__main__":
    log_formatter = logging.Formatter("[%(asctime)s] [%(levelname)s] %(message)s")
    app_log = logging.getLogger()
    app_log.setLevel(logging.DEBUG)
    sync = Sync()
    sync.sync_files()