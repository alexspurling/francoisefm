
import Adafruit_ADS1x15

import time


class AnalogueDial:

    def __init__(self):

        self.adc = Adafruit_ADS1x15.ADS1115()
        self.GAIN = 1

        # The ADC returns a value between 4450 and 26300
        # 4450 = low resistance; i.e. "high" volume
        # 26300 = high resistance; i.e. "low" volume
        # Solve two simultaneous equations (using y = mx + c) to convert to
        # values between 870 and 1070
        target_min = 1070
        target_max = 870
        min = 2500
        max = 26300
        self.m = (target_max - target_min) / (max - min)
        self.c = target_max - (self.m * max)

    def get_freq(self):

        value = self.adc.read_adc(1, gain=self.GAIN)
        freq = int(self.m * value + self.c)

        return freq, value

    def loop(self):
        # Main loop.
        while True:
            # Read all the ADC channel values in a list.
            freq, value = self.get_freq()

            print(freq / 10, value)

            # Pause for half a second.
            time.sleep(0.5)