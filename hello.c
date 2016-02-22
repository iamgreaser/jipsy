#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <sys/stat.h>
#include <unistd.h>

#include "shitlib.c"

void print_string(const char *s)
{
	int i;

	for(i = 0; s[i] != 0; i++)
		*((volatile uint8_t *)0x1FF00004) = (uint8_t)s[i];
}

const char out_ramp[] = " .,:;%$@";

int main(int argc, char *argv[])
{
	char buf[256];
	int32_t cr, ci;
	int x, y, i, reps;

	//sprintf(buf, "<%s> %i\n", "Hello World!", 12345);
	printf("<%s> %i\n", "Hello World!", 12345);
	//print_string(buf);
#define VW 150
#define VH 40
	for(reps = 0; reps < 1; reps++)
	{
		for(y = 0, ci = -0x14000; y < VH; y++, ci += (0x28000/VH))
		{
			for(x = 0, cr = -0x18000; x < VW; x++, cr += (0x20000/VW))
			{
				int32_t zr = 0;
				int32_t zi = 0;

#define MAX_REPS 64
				for(i = 0; i < MAX_REPS; i++)
				{
					int32_t tr = zr*zr - zi*zi;
					int32_t ti = 2*zr*zi;
					zr = tr + cr;
					zi = ti + ci;
					zr >>= 8;
					zi >>= 8;

					if(zr*zr + zi*zi > 0x20000)
						break;
				}

				*((volatile uint8_t *)0x1FF00004) = out_ramp[i&7];
			}

			*((volatile uint8_t *)0x1FF00004) = '\n';
		}
		*((volatile uint8_t *)0x1FF00004) = '\n';
	}

	return 0;
}

