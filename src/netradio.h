#ifndef NETRADIO
#define NETRADIO

#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define NUMMESS 4

#define ID 8

#define MESS 140

#define NBMESS 3

#define IP 15

#define PORT 4

#define NUMDIFF 2

int create_tcp_server(int port);
char *fill_with_zeros(int n, int size);
char *fill_with_sharp(char *s, int n);
char **get_msgs(char *filename, int *size);

#endif