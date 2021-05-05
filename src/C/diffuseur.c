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
#include <dirent.h>
#include "diffuseur.h"
#include "netradio.h"

#define min(a, b) (a <= b ? a : b)

#define file_dir "data/files/"

// Les informations du diffuseur actuel
diff_info di;

// La liste de message
char *msgList[MAX_MSG];
// Les index nécessaire
// Les accès concurrents sur NUM_MSG et MSG_INDEX sont tous situé
// à l'intérieur entre verrou
// Aucunes modifications concurrentes sur NBR_SENT
int NUM_MSG, MSG_INDEX, NBR_SENT = 0;
// Le verrou
pthread_mutex_t msgLock = PTHREAD_MUTEX_INITIALIZER;

int start();
int add_message(char *msg);
void mess(int sock);
void last(int sock);
void lsfi(int sock);
void dlfi(int sock);
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

// Incrémente le nombre de messages stockés
void incr_msg()
{
    NUM_MSG = (NUM_MSG + 1) % MAX_MSG;
}

// Incrémente l'index de la liste de message
void incr_msg_index()
{
    MSG_INDEX = (MSG_INDEX + 1) % NUM_MSG;
}

// Ajoute un message a la liste de message
int add_message(char *msg)
{
    pthread_mutex_lock(&msgLock);
    msgList[NUM_MSG] = fill_with_sharp(msg, ID + 1 + MESS);
    incr_msg();
    pthread_mutex_unlock(&msgLock);

    return 0;
}

// Gère la diffusion du diffuseur
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
        // on prend le verrou sur la liste des messages
        pthread_mutex_lock(&msgLock);
        // liste vide -> on déverouille et on réitère
        if (NUM_MSG == 0)
        {
            pthread_mutex_unlock(&msgLock);
            continue;
        }
        // on prend un message
        char *msg = msgList[MSG_INDEX];
        incr_msg_index();
        // et on unlock
        pthread_mutex_unlock(&msgLock);

        int size = 4 + 1 + NUMMESS + 1 + ID + 1 + MESS + 2;
        char buff[size];
        memset(buff, 0, size);
        char *num = fill_with_zeros(NBR_SENT, NUMMESS);
        sprintf(buff, "DIFF %s %s\r\n", num, msg);

        if(sendto(sock, buff, size * sizeof(char), 0, saddr, (socklen_t)sizeof(struct sockaddr_in)) == -1){
            perror("sendto");
            exit(-1);
        }

        NBR_SENT = (NBR_SENT + 1) % MAX_MSG;

        free(num);
        sleep(SLEEP_TIME);
    }
    return NULL;
}

// Permet de gérer les routines des clients via TCP
void *client_routine(void *adr)
{
    int sock = *((int *)adr);

    char cmd[5];
    memset(cmd, 0, 5);

    // On récupère la commande, et on execute la fonction correspondante
    if(recv(sock, cmd, 4 * sizeof(char), 0) < 0){
        perror("recv");
        close(sock);
        exit(-1);
    };

    if (!strcmp(cmd, "LAST"))
    {
        last(sock);
    }
    else if (!strcmp(cmd, "MESS"))
    {
        mess(sock);
    }
    else if (!strcmp(cmd, "LSFI")){
        lsfi(sock);
    }
    else if (!strcmp(cmd, "DLFI")){
        dlfi(sock);
    }
    else
    {
        close(sock);
    }
    return NULL;
}

// Récupère un message et l'ajoute a la liste
void mess(int sock)
{
    // On saute l'espace
    char space;

    if(recv(sock, &space, sizeof(char), 0) < 0){
        perror("recv");
        close(sock);
        exit(-1);
    };

    int size = ID + 1 + MESS + 2;
    char buff[size];
    memset(buff, 0, size);

    if (recv(sock, buff, size * sizeof(char), 0) != size)
    {
        perror("recv");
        close(sock);
        exit(-1);
    }

    // On retire \r\n 
    buff[size - 1] = 0;
    buff[size - 2] = 0;
    add_message(buff);

    int len = 6 * sizeof(char);
    sendall(sock, "ACKM\r\n", &len);

    close(sock);
}

// Renvoie les n derniers messages envoyés
void last(int sock)
{
    char space;
    recv(sock, &space, sizeof(char), 0);

    int size = NBMESS + 2;
    char nb[size];
    memset(nb, 0, size);

    if (recv(sock, nb, size * sizeof(char), 0) != size)
    {
        perror("recv");
        close(sock);
        exit(-1);
    }

    pthread_mutex_lock(&msgLock);

    // On prend une copie de NBR_SENT, qui ne bougera pas
    // (une sorte d'image figée au moment de la requête)
    int nb_sent_cpy = NBR_SENT;
    int n = min(atoi(nb), nb_sent_cpy + 1);

    for (int i = 0; i < n; i++)
    {
        char *s = msgList[(nb_sent_cpy - i) % NUM_MSG];
        int size = 4 + 1 + NUMMESS + 1 + ID + 1 + MESS + 2;

        char oldm[size];
        memset(oldm, 0, size);
        sprintf(oldm, "OLDM %s %s\r\n", fill_with_zeros(nb_sent_cpy - i, NUMMESS), s);

        size = size * sizeof(char);
        if(sendall(sock, oldm, &size) == -1){
            perror("sendall");
            close(sock);
            exit(-1);
        }
    }
    pthread_mutex_unlock(&msgLock);

    int len = 6 * sizeof(char);
    if(sendall(sock, "ENDM\r\n", &len) == -1){
        perror("sendall");
        close(sock);
        exit(-1);
    }

    close(sock);
}

char **get_files(char* path, int *size){
    DIR *d;
    struct dirent *dir;
    d = opendir(path);

    char **files = malloc(sizeof(char *));
    *size = 0;

    if (d)
    {
        while ((dir = readdir(d)) != NULL)
        {   
            if(!strcmp(dir->d_name,".") || !strcmp(dir->d_name,"..")) continue;
            files = realloc(files, (*size + 1) * sizeof(char*));
            files[*size] = strdup(dir->d_name);
            *size = *size + 1;
        }
        closedir(d);
    }
    return files;
}

void lsfi(int sock){
    // On reçoit le \r\n
    char space;
    if(recv(sock, &space, sizeof(char), 0) < 0){
        perror("recv");
        close(sock);
        exit(-1);
    }
    if(recv(sock, &space, sizeof(char), 0) < 0){
        perror("recv");
        close(sock);
        exit(-1);   
    }

    int nbfiles;
    char **filenames = get_files("data/files/", &nbfiles);

    int size = 4 + 1 + NBFILE + 2;
    char fls[size];
    memset(fls, 0, size);

    char *s = fill_with_zeros(nbfiles, NBFILE);

    sprintf(fls,"FINB %s\r\n",s);
    
    if(sendall(sock, fls, &size) == -1){
        perror("sendall");
        close(sock);
        exit(-1);
    }

    for(int i = 0; i < nbfiles; i++){
        int size = 4 + 1 + FILENAME + 2;

        char name[size];
        memset(name, 0, size);
        sprintf(name, "FILE %s\r\n",fill_with_sharp(filenames[i], FILENAME));

        if(sendall(sock, name, &size) == -1){
            perror("sendall");
            close(sock);
            exit(-1);
        }
    }

    int endf = 6;

    if(sendall(sock, "ENDF\r\n", &endf) == -1){
        perror("sendall");
        close(sock);
        exit(-1);
    }

    close(sock);
}

void dlfi(int sock){
    char space;
    if(recv(sock, &space, sizeof(char), 0) < 0){
        perror("recv");
        close(sock);
        exit(-1);
    }

    char filename[FILENAME + 2];
    memset(filename, 0, FILENAME + 2);
    
    if(recv(sock, filename, sizeof(char) * (FILENAME + 2), 0) != FILENAME + 2){
        perror("recv");
        close(sock);
        exit(-1);
    }

    // on retire \r\n
    filename[FILENAME + 1] = 0;
    filename[FILENAME] = 0;
    
    for(int i = FILENAME - 1; i >= 0; i--){
        if(filename[i] == '#') filename[i] = 0;
        else break;
    }

    // puis on va lire dans le fichier ciblé
    char path[strlen(file_dir) + strlen(filename) + 1];
    memset(path, 0, strlen(file_dir) + strlen(filename) + 1);
    sprintf(path, "%s%s", file_dir, filename);

    int fd = open(path, O_RDONLY);
    // Fichier non trouvé...
    if(fd < 0){
        int size = 6;
        if(sendall(sock, "FINF\r\n", &size) < 0){
            perror("sendall");
            close(sock);
            exit(-1);
        }
        return;
    }

    // Sinon récupère sa taille
    int file_size = lseek(fd, 0, SEEK_END);

    char *file_size_char = fill_with_zeros(file_size, FILESIZE);

    int fiak_size = 4 + 1 + FILESIZE + 2;
    char fiak[fiak_size];
    memset(fiak, 0, fiak_size);

    sprintf(fiak,"FIOK %s\r\n", file_size_char);

    // On envoie la taille
    if(sendall(sock, fiak, &fiak_size) < 0){
        perror("sendall");
        close(sock);
        exit(-1);
    }

    // On retourne au début
    lseek(fd, 0, SEEK_SET);

    int size;

    int buff_size = 8196;
    char buff[buff_size];
    memset(buff, 0, buff_size);

    // Puis on envoie tout le fichier
    while((size = read(fd, buff, buff_size)) > 0){
        if(sendall(sock, buff, &size) < 0){
            perror("sendall");
            close(sock);
            exit(-1);
        }
    }

    // On ferme le fichier
    close(fd);

    // Et le message final
    int endl = 6;
    if(sendall(sock, "ENDL\r\n", &endl) < 0){
        perror("sendall");
        close(sock);
        exit(-1);
    }

    close(sock);
}

// Permet de gérer les connexions entrantes en TCP
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

void *regi(void *x){
    char *args = ((char*) x);
    char *s = strchr(args, ' ');
    if(s == NULL) exit(-1);

    char ip[strlen(args) - strlen(s) + 1];
    memset(ip, 0, strlen(args) - strlen(s) + 1);
    memcpy(ip, args, strlen(args) - strlen(s));

    char port[strlen(s) - 1];
    memset(port, 0, strlen(s) - 1);
    memcpy(port, s + 1, strlen(s) - 1);

    int port_int = atoi(port);

    // - créer socket de connexion vers ip & port
    int socket = create_client_socket(ip, port_int);
    if(socket == -1){
        perror("connect"); return NULL;
    }

    // - envoyer regi avec normalized ip
    int size = 4 + 1 + ID + 1 + IP + 1 + PORT + 1 + IP + 1 + PORT + 2;
    char regibuf[size];
    memset(regibuf, 0, size);
    sprintf(regibuf,"REGI %s %s %d %s %d\r\n",
        di.id, normalize_ip(di.ipmulti), di.port1,
        normalize_ip(di.ip2), di.port2);

    size = size * sizeof(char);

    if(sendall(socket, regibuf, &size) == -1){
        perror("sendall");
        close(socket);
        exit(-1);
    }

    char regiresp[6];
    memset(regiresp, 0, 6);

    if(recv(socket, regiresp, 6 * sizeof(char), 0) < 0){
        perror("recv");
        close(socket);
        exit(-1);
    }

    if(!strcmp(regiresp,"REOK\r\n")){
        printf("Enregistrement réussi !\n");
    }else{
        printf("Echec de l'enregistrement...\n");
        return NULL;
    }

    // - attendre en boucle les "RUOK" et répondre "IMOK"
    while(1){
        char resp[6];
        memset(resp, 0, 6);

        if(recv(socket, resp, 6 * sizeof(char), 0) < 0){
            perror("recv");
            close(socket);
            exit(-1);
        }

        if(!strcmp(resp,"RUOK\r\n")){
            int len = 6 * sizeof(char);
            sendall(socket, "IMOK\r\n", &len);
        }
    }
    return NULL;
}

// Démarre le diffuseur
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
    // Puis on boucle sur l'entrée standard
    char *line = NULL;
    size_t len = 0;

    while(getline(&line, &len, stdin) != -1){
        line[strlen(line) - 1] = 0;
        if(!strncmp(line,"REGI",4)){
            char *args = line + 5;

            pthread_t th_regi;
            if (pthread_create(&th_regi, NULL, regi, args) != 0)
            {
                perror("pthread_create regi");
                exit(-1);
            }
        }
    }
    return 0;
}

// Affiche les informations du diffuseur
void print_infos(diff_info di){
    printf("#-----------------------------------#\n");
    printf(" * id : %s\n", di.id);
    printf(" * ip multi diffusion : %s\n",di.ipmulti);
    printf(" * port multi diffusion : %d\n",di.port1);
    printf(" * ip TCP : %s\n",di.ip2);
    printf(" * port TCP : %d\n", di.port2);
    printf("#-----------------------------------#\n");
}
