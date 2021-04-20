#ifndef DIFFUSEUR_H
#define DIFFUSEUR_H

typedef struct
{
    char *id;
    int port1, port2;
    char *ipmulti;
    char *ip2;
} diff_info;

#define MAX_MSG 10000
#define SLEEP_TIME 1

#endif
