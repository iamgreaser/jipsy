jipsy: a MIPS emulator written in java that took 8 hours to get Lua 5.1 working on

this could do with more things, until i have said more things this will do

feed it a statically-linked 32-bit LE MIPS-I soft-float ELF file and it'll at least try to run it

but don't expect glibc to work as it's not designed for Linux syscalls; rather it has an I/O port at 0x1FF00004 - write a byte to do putchar(), and read to do getchar()

have fun

