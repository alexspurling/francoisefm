from logging.handlers import RotatingFileHandler
import logging

import re
import random
import time

from sync import Sync
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

    def __init__(self, sync, audio, display):
        self.sync = sync
        self.audio = audio
        self.display = display

    def play(self, freq, target_freq):
        logging.info(f"Tuning to {freq} (target: {target_freq})")
        freq_files = self.sync.files_by_frequency[target_freq]
        random_index = random.randrange(0, len(freq_files))
        display.set_freq(freq)
        if freq == target_freq:
            display.display_station(freq_files[random_index]["name"])
            self.audio.play_track(freq_files, random_index)
        else:
            display.display_station(None)
            self.audio.play_nearby(freq_files, random_index)

    def tune(self, freq):
        if freq in self.sync.files_by_frequency:
            self.play(freq, freq)
        elif (freq + 1) in self.sync.files_by_frequency:
            self.play(freq, freq + 1)
        elif (freq - 1) in self.sync.files_by_frequency:
            self.play(freq, freq - 1)
        else:
            display.set_freq(freq)
            display.display_station(None)
            self.audio.play_static()


sync = Sync()
display = Display()
audio = Audio(display)
radio = Radio(sync, audio, display)

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
    radio.sync.sync_files()
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
        radio.sync.sync_files()
    except Exception as e:
        logging.exception(f"Error! {e}", exc_info=e)
        logging.error("Sleeping for 10s before trying again")
        time.sleep(10)
