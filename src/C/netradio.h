#ifndef NETRADIO
#define NETRADIO

#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <netdb.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "diffuseur.h"

#define min(a, b) (a <= b ? a : b)

#define _POSIX_C_SOURCE 200809L

#define NUMMESS 4

#define ID 8

#define MESS 140

#define NBMESS 3

#define IP 15

#define PORT 4

#define NUMDIFF 2

#define FILENAME 255

#define FILESIZE 7

#define NBFILE 3

int create_tcp_server(int port);
int create_client_socket(char *adr, int port);
int create_multicast_receive_socket(char *adr, int port);
char *fill_with_zeros(int n, int size);
char *fill_with_sharp(char *s, long unsigned int n);
char **get_msgs(char *filename, int *size);
diff_info load_settings(char *filename);
char *get_host_address();
char *normalize_ip(char *ip);
int sendall(int s, char *buf, int *len);

#endif
