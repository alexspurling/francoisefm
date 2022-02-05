import RPi.GPIO as GPIO
from encoder import Encoder


ENCODER_CLK = 17
ENCODER_DT = 18
BUTTON = 4


# Original Frequency dial based on a digital rotary encoder
class FrequencyDial:

    def __init__(self, frequency_changed):
        self.frequency = 1000
        self.frequency_changed = frequency_changed

        # Set the GPIO mode to their "Broadcom" numbers. i.e. the number
        # corresponding with the pin on the broadcom chip and described
        # in the raspberry pi docs as GPIOXX
        GPIO.setmode(GPIO.BCM)
        # set up button
        GPIO.setup(4, GPIO.IN)
        GPIO.add_event_detect(BUTTON, GPIO.FALLING, callback=self.button_pressed)
        # set up rotary encoder
        self.encoder = Encoder(ENCODER_DT, ENCODER_CLK, self.encoder_changed)

    def encoder_changed(self, value, direction):
        self.frequency = value + 1000
        self.frequency_changed(self.frequency)

    def button_pressed(self, value):
        print("Button pressed", value)

    def get_frequency(self):
        return self.frequency


