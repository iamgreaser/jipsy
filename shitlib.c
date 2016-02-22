#include <string.h>
#include <stdio.h>
#include <stdint.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/times.h>
#include <sys/time.h>
#include <unistd.h>

/*int internal_errno;
int *__errno _PARAMS ((void))
{
	return &internal_errno;
}*/

extern char _end[];
extern char _ftext[];
intptr_t xx_cur_brk = (intptr_t)_end;

ssize_t write(int fd, const void *buf, size_t amt)
{
	size_t i;

	if(fd == 1 || fd == 2)
	{
		for(i = 0; i < amt; i++)
			*((volatile uint8_t *)0x1FF00004) = ((uint8_t *)buf)[i];

		return amt;
	}

	// TODO!
	return amt;
}

void _exit(int status)
{
	//char sbuf[512]; sprintf(sbuf, "exit = %i\n", status); write(1, sbuf, strlen(sbuf));
	((void (*)(void))(0x00000000))();
	for(;;) {}
}

int kill(pid_t p, int sig)
{
	errno = EPERM;
	return -1;

}

int open(const char *pathname, int flags)
{
	//char sbuf[512]; sprintf(sbuf, "fname = \"%s\", flags = %i\n", pathname, flags); write(1, sbuf, strlen(sbuf));
	errno = EACCES;
	return -1;
}

int fstat(int fd, struct stat *buf)
{
	//char sbuf[512]; sprintf(sbuf, "fstat = %i\n", fd); write(1, sbuf, strlen(sbuf));
	memset(buf, 0, sizeof(struct stat));
	return 0;
}

ssize_t read(int fd, void *buf, size_t amt)
{
	int i;
	//char sbuf[512]; sprintf(sbuf, "read = %i, amt = %i\n", fd, amt); write(1, sbuf, strlen(buf));

	if(fd == 0)
	{
		/*
		for(i = 0; i < amt; i++)
			((char *)buf)[i] = (char)*((volatile uint8_t *)0x1FF00004);

		return amt;
		*/
		if(amt == 0) return 0;
		int v = *((volatile uint32_t *)0x1FF00004);
		if(v < 0) return 0;
		((char *)buf)[0] = (char)v;
		return 1;
	}

	return 0;
}

off_t lseek(int fd, off_t offset, int whence)
{
	// TODO!
	//char sbuf[512]; sprintf(sbuf, "lseek = %i, off = %i, whence = %i\n", fd, offset, whence); write(1, sbuf, strlen(sbuf));
	return offset;
}

int isatty(int fd)
{
	//char sbuf[512]; sprintf(sbuf, "isatty = %i\n", fd); write(1, sbuf, strlen(sbuf));
	return (fd >= 0 && fd <= 2);
}

pid_t getpid(void)
{
	//char sbuf[512]; sprintf(sbuf, "getpid\n"); write(1, sbuf, strlen(sbuf));
	return 999;
}

int gettimeofday(struct timeval *restrict tv, void *restrict tz)
{
	if(tv != NULL)
	{
		tv->tv_sec = 0;
		tv->tv_usec = 0;
	}

	return 0;
}

clock_t times(struct tms *buf)
{
	// TODO: wallclock in emulator
	buf->tms_utime = 0;
	buf->tms_stime = 0;
	buf->tms_cutime = 0;
	buf->tms_cstime = 0;

	return 0;
}

int unlink(const char *pathname)
{
	errno = EACCES;
	return -1;
}

int link(const char *oldpath, const char *newpath)
{
	errno = EPERM;
	return -1;
}

int close(int fd)
{
	char sbuf[512]; sprintf(sbuf, "close = %i\n", fd); write(1, sbuf, strlen(sbuf));
	return 0;
}

void *sbrk(intptr_t increment)
{
	char *oldbrk = (char *)xx_cur_brk;
	xx_cur_brk += increment;
	intptr_t tbrk = (intptr_t)xx_cur_brk;
	//tbrk = (tbrk+0xFFF)&~0xFFF;

#if 0
	char sbuf[512]; sprintf(sbuf, "RAM usage: %iKB heap %iKB total %iKB from start - break is %p\n"
		, ((vbrk-_end)+512)>>10
		, ((vbrk-_ftext)+512)>>10
		, (tbrk+512)>>10
		, (char *)vbrk
		); write(1, sbuf, strlen(sbuf));
#endif
	return (void *)oldbrk;
}

int brk(void *pabs)
{
	char sbuf[512]; sprintf(sbuf, "brk = %p\n", pabs); write(1, sbuf, strlen(sbuf));
	return -1;
}

extern int main(int argc, char *argv[]);
extern int _gp[];
void _start(void)
{
	asm volatile (
		"lui $gp, %%hi(%0)\n"
		"addiu $gp, $gp, %%lo(%0)\n"
		:
		: "i"(_gp)
		: 
	);

	char *argv_base[2] = {
		"shitlib_launcher",
		NULL
	};

	main(1, argv_base);
}

