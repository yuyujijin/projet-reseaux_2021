#include "netradio.h"

int create_tcp_server(int port)
{
    int sock = socket(PF_INET, SOCK_STREAM, 0);

    struct sockaddr_in address_sock;
    address_sock.sin_family = AF_INET;
    address_sock.sin_port = htons(port);
    address_sock.sin_addr.s_addr = htonl(INADDR_ANY);

    if (bind(sock, (struct sockaddr *)&address_sock, sizeof(struct sockaddr_in)))
    {
        perror("bind");
        return -1;
    }
    return sock;
}

char *fill_with_zeros(int n, int size)
{
    char *s = malloc(sizeof(char) * size);
    if (s == NULL)
        return NULL;
    memset(s, 0, size);

    char nstring[size];
    memset(nstring, 0, size);
    sprintf(nstring, "%d", n);

    memset(s, '0', size - strlen(nstring));
    memcpy(s + (size - strlen(nstring)), nstring, strlen(nstring));

    return s;
}

char *fill_with_sharp(char *s, int n)
{
    if (strlen(s) >= n)
    {
        return strndup(s, n);
    }
    char *ns = malloc(sizeof(char) * n);
    if (ns == NULL)
        return NULL;
    memset(ns, 0, n);
    memcpy(ns, s, strlen(s));
    memset(ns + strlen(s), '#', n - strlen(s));
    return ns;
}

char **get_msgs(char *filename, int *size)
{
    FILE *fp = fopen(filename, "r");
    if (fp == NULL)
        exit(-1);
    char *line = NULL;
    size_t len = 0;

    *size = 0;
    while (getline(&line, &len, fp) != -1)
    {
        *size += 1;
    }

    char **msgs = malloc(sizeof(char *) * (*size));
    if (msgs == NULL)
        exit(-1);

    rewind(fp);
    int index = 0;
    while (getline(&line, &len, fp) != -1)
    {
        msgs[index] = strdup(line);
        if (msgs[index][strlen(msgs[index]) - 1] == '\n')
            msgs[index][strlen(msgs[index]) - 1] = 0;
        index++;
    }

    return msgs;
}
