from enum import Enum
import pygame
import time

pygame.mixer.init()


class Mode(Enum):
    OFF = 1,
    STATIC = 2,
    NEARBY = 3,
    PLAYING = 4


# Adjust value by step but cap at 0 and 1
def adjust(val, step):
    if step < 0 and val <= 0:
        return 0
    elif step > 0 and val >= 1:
        return 1
    return val + step


class Audio:

    def __init__(self):
        self.static = pygame.mixer.Channel(0)
        self.static.set_volume(0)
        # Immediately start playing static in a loop but with 0 volume
        self.static.play(pygame.mixer.Sound("sound/radiotuning.mp3"), -1)
        # Create a different channel to play the actual audio
        self.track = pygame.mixer.Channel(1)
        self.track_nearby = pygame.mixer.Channel(2)
        self.track_list = []
        self.track_index = 0
        self.mode = Mode.OFF

    def play_static(self):
        if self.mode == Mode.OFF:
            self.mode = Mode.STATIC
            print("Tuning to static")

            self.fade_static(1, Mode.STATIC)

        elif self.mode == Mode.PLAYING or self.mode == Mode.NEARBY:
            self.mode = Mode.STATIC
            print("Tuning to static")

            self.track.stop()
            self.track_nearby.stop()

            self.fade_static(1, Mode.STATIC)

    def play_track(self, files: [str], index: int):
        if self.mode == Mode.OFF:
            self.mode = Mode.PLAYING

            self.play_now(files, index, False)
            self.fade_static(0, Mode.PLAYING)

        if self.mode == Mode.STATIC:
            self.mode = Mode.PLAYING

            self.play_now(files, index, False)
            self.fade_static(0, Mode.PLAYING)

        if self.mode == Mode.NEARBY:
            self.mode = Mode.PLAYING

            # Don't try to start the track from the beginning, just adjust the volumes
            self.track.set_volume(1)
            self.track_nearby.set_volume(0)

            self.fade_static(0, Mode.PLAYING)

    def play_nearby(self, files: [str], index: int):
        if self.mode == Mode.OFF:
            self.mode = Mode.NEARBY

            self.play_now(files, index, True)
            self.fade_static(0.1, Mode.NEARBY)

        if self.mode == Mode.STATIC:
            self.mode = Mode.NEARBY

            self.play_now(files, index, True)
            self.fade_static(0.1, Mode.NEARBY)

        if self.mode == Mode.PLAYING:
            self.mode = Mode.NEARBY

            # Don't try to start the track from the beginning, just adjust the volumes
            self.track.set_volume(0)
            self.track_nearby.set_volume(1)

            self.fade_static(0.1, Mode.NEARBY)

    def fade_static(self, target, while_mode_is):
        static_vol = self.static.get_volume()
        print(f"static target: {target} cur vol: {static_vol}")
        while static_vol > target and self.mode == while_mode_is:
            static_vol -= 0.01
            self.static.set_volume(static_vol)
            time.sleep(0.01)
        while static_vol < target and self.mode == while_mode_is:
            static_vol += 0.01
            self.static.set_volume(static_vol)
            time.sleep(0.01)
        print(f"Now set to: {static_vol}")

    def play_now(self, track_list, index, nearby: bool):
        # Play the given file at the index now
        self.track_list = track_list
        self.track_index = index
        file: str = self.track_list[index]
        nearby_file = file[0:file.rindex(".")] + "-lowpass" + file[file.rindex("."):]

        print("Playing track: " + nearby_file if nearby else file)

        self.track.play(pygame.mixer.Sound(file))
        self.track_nearby.play(pygame.mixer.Sound(nearby_file))
        if nearby:
            self.track.set_volume(0)
            self.track_nearby.set_volume(1)
        else:
            self.track.set_volume(1)
            self.track_nearby.set_volume(0)

    def check_next_track(self):
        print("Checking busy status")
        # Check if the last track has ended. if so then play the next in the list
        if not self.track.get_busy():
            print("Everything's quiet. Play the next track")
            self.track_index = (self.track_index + 1) % len(self.track_list)
            self.play_now(self.track_list, self.track_index, self.mode == Mode.NEARBY)
