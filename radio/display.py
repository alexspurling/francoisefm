# Raspberry PI pin layout for display
#            ...              |
# 3V3               RED       |   GPIO24    DC      PURPLE
# GPIO10  SPI MOSI  YELLOW    |   GROUND            ORANGE
#            =                |   GPIO25   RESET    GREY
# GPIO11  SPI CLK   GREEN     |   GPIO08   CS       BLUE
#            ...              |            =

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

    def clear(self):
        # Draw a black filled box to clear the image.
        self.draw.rectangle((0, 0, self.width, self.height), outline=0, fill=0)

    def display_station(self, freq, name):
        self.clear()

        freq_str = str(freq / 10)
        self.draw.text((0, 0), f"{freq_str}Mhz ", font=self.font, fill=255)

        if name:
            self.draw.text((0, 12), f"{name} ", font=self.font2, fill=255)
        else:
            self.draw.text((0, 12), f"- ", font=self.font2, fill=255)

        # Display image.
        self.disp.image(self.image)
        self.disp.display()


    # def sine_wave(self):
    #     for x in range(0, 128):
    #         y = int(math.sin((x + self.count) / (128 / (2 * math.pi))) * 8 + 23)
    #         # print(x, y)
    #         self.px[x, y] = 255

# "Afgjqwyç,ç,Çâêîôûé,àèùëïü,AaÅåÆæFæfHhåJjådKkåLælMæmNænØøOoSæsXæksYyZsæt,"