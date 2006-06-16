#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <assert.h>

#define BUF_SIZE 4000

class FileWriter_state {
public:
  FileWriter_state() { file_handle = -1; buf_index = 0; }
  int file_handle;
  int file_offset, file_length;
  char file_buf[BUF_SIZE];
  int buf_index;
};

int FileWriter_open(char *pathname);

void FileWriter_close(int fs_ptr);

int FileWriter_flush(int fs_ptr);

int FileWriter_getpos(int fs_ptr);

void FileWriter_setpos(int fs_ptr, int pos);

template<class T>
int FileWriter_write(int fs_ptr, T data);
