#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <pthread.h>
#include <errno.h>
#include <sys/types.h>
#include <netdb.h>
#include "diffuseur.h"
#include "netradio.h"

#define min(a, b) (a <= b ? a : b)

// Les informations du diffuseur actuel
diff_info di;

// La liste de message
char *msgList[MAX_MSG];
// Les index nécessaire
int NUM_MSG, MSG_INDEX, NBR_SENT = 0;
// Le verrou
pthread_mutex_t msgLock = PTHREAD_MUTEX_INITIALIZER;

int start();
int add_message(char *msg);
void mess(int sock);
void last(int sock);
void print_infos(diff_info di);

int main(int argc, char *argv[])
{   
    if (argc < 2)
    {
        printf("Veuillez indiquer un fichier de settings.\n");
        return -1;
    }
    if(argc < 3){
        printf("Veuillez indiquer un fichier de messages.\n");
        return -1;
    }

    // On récupère les réglages
    di = load_settings(argv[1]);
    di.ip2 = get_host_address();

    print_infos(di);

    // Puis les messages de base
    int size = 0;
    char **msgs = get_msgs(argv[2], &size);

    // Puis on charge les messages
    for (int i = 0; i < size; i++)
    {
        char buff[ID + 1 + strlen(msgs[i]) + 1];
        memset(buff, 0, ID + 1 + strlen(msgs[i]) + 1);
        strncpy(buff, di.id, ID);
        buff[ID] = ' ';

        memcpy(buff + ID + 1, msgs[i], strlen(msgs[i]));

        add_message(buff);
    }

    // Et on lance le diffuseur
    start();
    return 0;
}

void incr_msg()
{
    NUM_MSG = (NUM_MSG + 1) % MAX_MSG;
}

void incr_msg_index()
{
    MSG_INDEX = (MSG_INDEX + 1) % NUM_MSG;
}

int add_message(char *msg)
{
    pthread_mutex_lock(&msgLock);
    msgList[NUM_MSG] = fill_with_sharp(msg, ID + 1 + MESS);
    incr_msg();
    pthread_mutex_unlock(&msgLock);

    return 0;
}

void *diffuse()
{
    int sock = socket(PF_INET, SOCK_DGRAM, 0);
    struct addrinfo *fi;
    struct addrinfo h;
    memset(&h, 0, sizeof(struct addrinfo));
    h.ai_family = AF_INET;
    h.ai_socktype = SOCK_DGRAM;
    char port[5];
    memset(port, 0, 5);
    sprintf(port, "%d", di.port1);
    int r = getaddrinfo(di.ipmulti, port, NULL, &fi);
    if (r != 0 || fi == NULL)
    {
        perror("getaddrinfo");
        exit(-1);
    }
    struct sockaddr *saddr = fi->ai_addr;
    while (1)
    {
        pthread_mutex_lock(&msgLock);
        if (NUM_MSG == 0)
        {
            pthread_mutex_unlock(&msgLock);
            continue;
        }
        char *msg = msgList[MSG_INDEX];
        incr_msg_index();
        pthread_mutex_unlock(&msgLock);

        int size = 4 + 1 + NUMMESS + 1 + ID + 1 + MESS + 2;
        char buff[size];
        memset(buff, 0, size);
        char *num = fill_with_zeros(NBR_SENT, NUMMESS);
        sprintf(buff, "DIFF %s %s\r\n", num, msg);

        sendto(sock, buff, size, 0, saddr, (socklen_t)sizeof(struct sockaddr_in));
        NBR_SENT = (NBR_SENT + 1) % MAX_MSG;

        free(num);
        sleep(SLEEP_TIME);
    }
    return NULL;
}

void *client_routine(void *adr)
{
    int sock = *((int *)adr);

    char cmd[5];
    memset(cmd, 0, 5);

    recv(sock, cmd, 4 * sizeof(char), 0);

    if (!strcmp(cmd, "LAST"))
    {
        last(sock);
    }
    else if (!strcmp(cmd, "MESS"))
    {
        mess(sock);
    }
    else
    {
        close(sock);
    }
    return NULL;
}

void mess(int sock)
{
    // On saute l'espace
    char space;
    recv(sock, &space, sizeof(char), 0);
    int size = ID + 1 + MESS + 2;
    char buff[size];
    memset(buff, 0, size);

    if (recv(sock, buff, size, 0) != size)
    {
        perror("recv");
        close(sock);
        exit(-1);
    }

    buff[size - 1] = 0;
    buff[size - 2] = 0;
    add_message(buff);

    send(sock, "ACKM\r\n", sizeof(char) * 6, 0);

    close(sock);
}

void last(int sock)
{
    char space;
    recv(sock, &space, sizeof(char), 0);

    int size = NBMESS + 2;
    char nb[size];
    memset(nb, 0, size);

    if (recv(sock, nb, size, 0) != size)
    {
        perror("recv");
        close(sock);
        exit(-1);
    }

    pthread_mutex_lock(&msgLock);
    int n = min(atoi(nb), NBR_SENT + 1);

    for (int i = 0; i < n; i++)
    {
        char *s = msgList[(NBR_SENT - i) % NUM_MSG];
        int size = 4 + 1 + NUMMESS + 1 + ID + 1 + MESS + 2;

        char oldm[size];
        memset(oldm, 0, size);
        sprintf(oldm, "OLDM %s %s\r\n", fill_with_zeros(NBR_SENT - i, NUMMESS), s);

        send(sock, oldm, size, 0);
    }
    pthread_mutex_unlock(&msgLock);

    send(sock, "ENDM\r\n", 6, 0);

    close(sock);
}

void *receive()
{
    // On créer un server
    int sock = create_tcp_server(di.port2);

    // On écoute sur la socket
    if (listen(sock, 0))
    {
        perror("listen");
        exit(-1);
    }

    // Puis on récupère toutes les connexions entrantes
    while (1)
    {
        struct sockaddr_in caller;
        socklen_t size = sizeof(caller);

        int sock_cli = accept(sock, (struct sockaddr *)&caller, &size);
        if (sock_cli < 0)
            continue;

        pthread_t th_client;
        if (pthread_create(&th_client, NULL, client_routine, &sock_cli) != 0)
        {
            perror("pthread_create diffuse");
            exit(-1);
        }
    }
    return NULL;
}

int start()
{
    pthread_t th_diff;
    if (pthread_create(&th_diff, NULL, diffuse, NULL) != 0)
    {
        perror("pthread_create diffuse");
        exit(-1);
    }
    pthread_t th_receive;
    if (pthread_create(&th_receive, NULL, receive, NULL) != 0)
    {
        perror("pthread_create receive");
        exit(-1);
    }
    read(STDIN_FILENO, NULL, 1);
    return 0;
}

void print_infos(diff_info di){
    printf("#-----------------------------------#\n");
    printf(" * id : %s\n", di.id);
    printf(" * ip multi diffusion : %s\n",di.ipmulti);
    printf(" * port multi diffusion : %d\n",di.port1);
    printf(" * ip TCP : %s\n",di.ip2);
    printf(" * port TCP : %d\n", di.port2);
    printf("#-----------------------------------#\n");
}
