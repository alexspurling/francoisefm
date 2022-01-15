# Raspberry PI pin layout for display
#            ...              |
# 3V3               RED       |   GPIO24    DC      PURPLE
# GPIO10  SPI MOSI  YELLOW    |   GROUND            ORANGE
#            =                |   GPIO25   RESET    GREY
# GPIO11  SPI CLK   GREEN     |   GPIO08   CS       BLUE
#            ...              |            =
import logging
import threading
import time
import waveshare.SPI as SPI
import waveshare.SSD1305 as SSD1305

from PIL import Image
from PIL import ImageDraw
from PIL import ImageFont

import math

# Raspberry Pi pin configuration:
RST = None     # on the PiOLED this pin isnt used
# Note the following are only used with SPI:
DC = 24
SPI_PORT = 0
SPI_DEVICE = 0


class Display:

    def __init__(self):
        # 128x32 display with hardware SPI:
        self.disp = SSD1305.SSD1305_128_32(rst=RST, dc=DC, spi=SPI.SpiDev(SPI_PORT, SPI_DEVICE, max_speed_hz=8000000))

        # Initialize library.
        self.disp.begin()

        # Clear display.
        self.disp.clear()
        self.disp.display()

        # Create blank image for drawing.
        # Make sure to create image with mode '1' for 1-bit color.
        self.width = self.disp.width
        self.height = self.disp.height
        self.image = Image.new('1', (self.width, self.height))
        # Get drawing object to draw on image.
        self.draw = ImageDraw.Draw(self.image)

        # Some other nice fonts to try: http://www.dafont.com/bitmap.php
        self.font = ImageFont.truetype('waveshare/pixel font-7.ttf', 14)
        self.font2 = ImageFont.truetype('waveshare/pixel font-7.ttf', 22)

        self.clear()
        self.px = self.image.load()

        self.stop_thread = True
        self.thread_stopped = True

    def clear(self):
        # Draw a black filled box to clear the image.
        self.draw.rectangle((0, 0, self.width, self.height), outline=0, fill=0)

    def display_station_at_x(self, freq, name, x):
        self.clear()

        freq_str = str(freq / 10)

        if name:
            self.draw.text((40, 0), f"{freq_str}Mhz ", font=self.font, fill=255)
            self.draw.text((x, 12), f"{name} ", font=self.font2, fill=255)
        else:
            self.draw.text((25, 0), f"{freq_str}Mhz ", font=self.font2, fill=255)

        # Display image.
        self.disp.image(self.image)
        self.disp.display()

    def display_name_thread(self, freq, name):
        try:
            x = 0

            size = self.font2.getsize(name)
            while not self.stop_thread:
                self.display_station_at_x(freq, name, x)

                x -= 1
                if x < -size[0]:
                    x = 128

                time.sleep(0.01)
        except Exception as e:
            logging.exception("Error in display thread", exc_info=e)
        self.thread_stopped = True

    def display_station(self, freq, name):
        self.stop_thread = True
        # Wait until the thread has stopped before starting a new one
        while not self.thread_stopped:
            time.sleep(0.1)

        # start new thread if name is > 11 chars so we can animate the display
        if name and len(name) > 11:
            t = threading.Thread(target=self.display_name_thread, args=(freq, name))
            self.stop_thread = False
            self.thread_stopped = False
            t.start()
            # self.display_station_at_x(freq, name, 10)
        else:
            self.display_station_at_x(freq, name, 0)

# "Afgjqwyç,ç,Çâêîôûé,àèùëïü,AaÅåÆæFæfHhåJjådKkåLælMæmNænØøOoSæsXæksYyZsæt,"