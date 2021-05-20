#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>
#include "netradio.h"
#include <sys/wait.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>

char *helps[] = { "'LSTN' : Begin listening to a specified diffusor.",
            "'LIST' : Ask for a list of diffusor to a diffusor manager.",
            "'LAST' : Ask for the n last messages of a diffusor", "'exit' : Leaves the client.",
            "'HELP' : Print every possible commands.", "'MESS' : Send a client message to a diffusor.", 
            "'LSFI' : Ask the diffusor for a list of available files to download.",
            "'DLFI' : Download a file.", NULL};

void* lstn(void *);
void list(char *);
void last(char *);
void mess(char *);
void lsfi(char *);
void dlfi(char *);
void help();

char *id = NULL;
int listening = 0;

int main(void){
    // On boucle sur l'entrée standard
    size_t len = 0;
    char *line = NULL;

    printf("Welcome to the NetRadio client !\n");
    printf("Please enter your username :-)\n");

    getline(&line, &len, stdin);

    line[strlen(line) - 1] = 0;
    id = fill_with_sharp(line, ID);

    printf("Welcome, %s!\n", id);

    printf("Type 'HELP' to print every commands and 'exit' to exit the client.\n");

    while(getline(&line, &len, stdin) != -1){
        // On retire le \n
        line[strlen(line) - 1] = 0;
        // On cherche la commande demandée
        if(!strncmp("LSTN", line, 4)){
            listening = 1;
            pthread_t th_listen;
            if (pthread_create(&th_listen, NULL, lstn, line) != 0)
            {
                perror("pthread_create listen");
                exit(-1);
            }
        }else if(!strncmp("LIST", line, 4)){
            list(line);
        }else if(!strncmp("LAST", line, 4)){
            last(line);
        }else if(!strncmp("MESS", line, 4)){
            mess(line);
        }else if(!strncmp("HELP", line, 4)){
            help();
        }else if(!strncmp("LSFI", line, 4)){
            lsfi(line);
        }else if(!strncmp("DLFI", line, 4)){
            dlfi(line);
        }else if(!strncmp("exit", line, 4)){
            break;
        }else{
            printf("Unknown command \"%s\". Type \"HELP\""
            " to get a list of every possible commands.\n", line);
        }
        while(listening){}
    }
    return 0;
}

void* lstn(void *line){
    char *args = ((char *) line) + 5;
    char *s = strchr(args, ' ');
    if(s == NULL) exit(-1);

    char ip[strlen(args) - strlen(s) + 1];
    memset(ip, 0, strlen(args) - strlen(s) + 1);
    memcpy(ip, args, strlen(args) - strlen(s));

    char port[strlen(s) - 1];
    memset(port, 0, strlen(s) - 1);
    memcpy(port, s + 1, strlen(s) - 1);

    int port_int = atoi(port);

    int sock = create_multicast_receive_socket(ip, port_int);
    if(sock < 0){
        perror("create_multicast_receive_socket");
        return NULL;
    }

    printf("Now listening to %s:%d...\n", ip, port_int);

    printf("Please enter the `tty` of the window you would like your diffusion to be shown\n");

    char *line_n = NULL;
    size_t len = 0;

    getline(&line_n, &len, stdin);

    listening = 0;

    line_n[strlen(line_n) - 1] = 0;

    int tty = atoi(line_n);

    char ttyname[64];
    memset(ttyname, 0, 64);
    sprintf(ttyname, "/dev/pts/%d", tty);

    int fd = open(ttyname, O_RDWR);
    if(fd < 0){
        perror("open");
        return NULL;
    }

    printf("Diffusion is now redirected to %s\n", ttyname);

    while(1){
        int len = 4 + 1 + NUMMESS + 1 + ID + 1 + MESS + 2;
        char buf[len + 1];
        memset(buf, 0, len + 1);
        recv(sock, buf, len, 0);

        buf[strlen(buf) - 1] = 0;
        buf[strlen(buf) - 1] = 0;

        for(int i = strlen(buf) - 1; i >= 0; i--){
            if(buf[i] != '#') break;
            buf[i] = 0;
        }

        buf[strlen(buf)] = '\n';

        // Puis on supprime le début du message
        memmove(buf, buf + 4 + 1 + 1, strlen(buf) - 4 - 1 + 1);

        if(write(fd, buf, strlen(buf)) < 0) break;
    }

    printf("Stopped listening to %s:%d...\n",ip, port_int);

    close(sock);

    return NULL;
}

void list(char *line){
    char *args = line + 5;
    char *s = strchr(args, ' ');
    if(s == NULL) exit(-1);

    char ip[strlen(args) - strlen(s) + 1];
    memset(ip, 0, strlen(args) - strlen(s) + 1);
    memcpy(ip, args, strlen(args) - strlen(s));

    char port[strlen(s) - 1];
    memset(port, 0, strlen(s) - 1);
    memcpy(port, s + 1, strlen(s) - 1);

    int port_int = atoi(port);

    int sock = create_client_socket(ip, port_int);
    if(sock < 0){
        perror("create_client_socket");
        return;
    }

    int len = 6;
    if(sendall(sock, "LIST\r\n", &len) < 0){
        perror("sendall");
        return;
    }

    int nbrsize = 4 + 1 + NUMDIFF + 2;
    char nbr[nbrsize];
    memset(nbr, 0, nbrsize);

    if(recv(sock, nbr, nbrsize, 0) != nbrsize){
        perror("recv");
        return;
    }

    nbr[nbrsize - 1] = 0;
    nbr[nbrsize - 2] = 0;
    int n = atoi(nbr + 5);
    printf("%d diffusor%s registered here.", n, (n != 1) ? "s are" : " is");

    if(n > 0){
        printf(" Here is the list of every of them :\n");
        printf("%-5s %-9s %-16s %-5s %-16s %-5s\n", "#", "ID", "IP1", "PORT1", "IP2", "PORT2");
    }else{
        printf("\n");
    }

    for(int i = 0; i < n; i++){
        int length = 4 + 1 + ID + 1 + IP + 1 + PORT + 1 + IP + 1 + PORT + 2;
        char item[length + 1];
        memset(item, 0, length + 1);

        if(recv(sock, item, length, 0) != length){
            perror("recv");
            return;
        }

        // On retire \r\n
        item[length - 1] = 0;
        item[length - 2] = 0;

        char *elems[6];
        int index = 0;

        char * strToken = strtok ( item, " " );
        while ( strToken != NULL ) {
            elems[index++] = strdup(strToken);
            // On demande le token suivant.
            strToken = strtok ( NULL, " " );
        }

        printf("%-5d %-9s %-16s %-5s %-16s %-5s\n", i + 1, elems[1], elems[2], elems[3], elems[4], elems[5]);
    }

    close(sock);

}

void mess(char *line){
    char *args = line + 5;
    char *s = strchr(args, ' ');
    if(s == NULL) exit(-1);

    char ip[strlen(args) - strlen(s) + 1];
    memset(ip, 0, strlen(args) - strlen(s) + 1);
    memcpy(ip, args, strlen(args) - strlen(s));

    char port[strlen(s) - 1];
    memset(port, 0, strlen(s) - 1);
    memcpy(port, s + 1, strlen(s) - 1);

    int port_int = atoi(port);

    int sock = create_client_socket(ip, port_int);
    if(sock < 0){
        perror("create_client_socket");
        return;
    }

    printf("Please enter your message (Remember: If your message is longer than %d"
    " characters, it will be truncated): ", MESS);

    char *line_n = NULL;
    size_t len = 0;

    getline(&line_n, &len, stdin);

    line_n[strlen(line_n) - 1] = 0;

    int size = 4 + 1 + ID + 1 + MESS + 2;
    char mess[size + 1];
    memset(mess, 0, size + 1);

    sprintf(mess, "MESS %s %s\r\n", id, fill_with_sharp(line_n, MESS));

    if(sendall(sock, mess, &size) < 0){
        perror("sendall");
        return;
    }

    char resp[7];
    memset(resp, 0, 7);
    if(recv(sock, resp, 6, 0) != 6){
        perror("recv");
        return;
    }

    if(!strcmp(resp, "ACKM\r\n")){
        printf("Message sent successfully !\n");
    }else{
        printf("We couldn't send your message as there was an error with it, sorry!\n");
    }

    close(sock);
}


void last(char *line){
    char *args = line + 5;
    char *s = strchr(args, ' ');
    if(s == NULL) exit(-1);

    char ip[strlen(args) - strlen(s) + 1];
    memset(ip, 0, strlen(args) - strlen(s) + 1);
    memcpy(ip, args, strlen(args) - strlen(s));

    char port[strlen(s) - 1];
    memset(port, 0, strlen(s) - 1);
    memcpy(port, s + 1, strlen(s) - 1);

    int port_int = atoi(port);

    int sock = create_client_socket(ip, port_int);
    if(sock < 0){
        perror("create_client_socket");
        return;
    }

    printf("Enter a number between 0 and 999 (included)\n");
    char *line_n = NULL;
    size_t len = 0;

    getline(&line_n, &len, stdin);

    line_n[strlen(line_n) - 1] = 0;
    int n = atoi(line_n);

    while(n < 0 || n > 999){
        getline(&line_n, &len, stdin);    
    
        line_n[strlen(line_n) - 1] = 0;
        n = atoi(line_n);
    }

    int size = 4 + 1 + NBMESS + 2;
    char last[size];
    memset(last, 0, size);
    sprintf(last, "LAST %s\r\n", fill_with_zeros(n, NBMESS));

    if(sendall(sock, last, &size) < 0){
        perror("sendall");
        return;
    }

    char cmd[4];
    memset(cmd, 0, 4);
    if(recv(sock, cmd, 4, 0) != 4){
        perror("recv");
        return;
    }

    while(strcmp(cmd, "ENDM")){
        char s;
        if(recv(sock, &s, 1, 0) != 1){
            perror("recv");
            return;
        }

        int size = NUMMESS + 1 + ID + 1 + MESS + 2;

        char item[size + 1];
        memset(item, 0, size + 1);
        if(recv(sock, item, size, 0) != size){
            perror("recv");
            return;
        }

        // On retire \r\n
        item[size - 1] = 0;
        item[size - 2] = 0;

        // On retire les #
        for(int i = strlen(item) - 1; i >= 0; i--){
            if(item[i] != '#') break;
            item[i] = 0;
        }

        printf("%s\n", item);

        memset(cmd, 0, 4);
        if(recv(sock, cmd, 4, 0) != 4){
            perror("recv");
            return;
        }
    }

    // On videles \r\n restant
    if(recv(sock, cmd, 2, 0) != 2){
        perror("recv");
        return;
    }

    close(sock);
}

void lsfi(char *line){
    char *args = line + 5;
    char *s = strchr(args, ' ');
    if(s == NULL) exit(-1);

    char ip[strlen(args) - strlen(s) + 1];
    memset(ip, 0, strlen(args) - strlen(s) + 1);
    memcpy(ip, args, strlen(args) - strlen(s));

    char port[strlen(s) - 1];
    memset(port, 0, strlen(s) - 1);
    memcpy(port, s + 1, strlen(s) - 1);

    int port_int = atoi(port);

    int sock = create_client_socket(ip, port_int);
    if(sock < 0){
        perror("create_client_socket");
        return;
    }

    int len = 6;
    if(sendall(sock, "LSFI\r\n", &len) < 0){
        perror("sendall");
        return;
    }

    int nbrsize = 4 + 1 + NBFILE + 2;
    char nbr[nbrsize];
    memset(nbr, 0, nbrsize);

    if(recv(sock, nbr, nbrsize, 0) != nbrsize){
        perror("recv");
        return;
    }

    nbr[nbrsize - 1] = 0;
    nbr[nbrsize - 2] = 0;
    int n = atoi(nbr + 5);
    printf("%d file.s registered here.\n", n);

    for(int i = 0; i < n; i++){
        int length = 4 + 1 + FILENAME + 2;
        char item[length + 1];
        memset(item, 0, length + 1);

        if(recv(sock, item, length, 0) != length){
            perror("recv");
            return;
        }

        // On retire \r\n
        item[length - 1] = 0;
        item[length - 2] = 0;

        // On retire les #
        for(int i = strlen(item) - 1; i >= 0; i--){
            if(item[i] != '#') break;
            item[i] = 0;
        }

        printf(" * %s\n", item + 5);
    }
    close(sock);
}

void dlfi(char *line){
    char *args = line + 5;
    char *s = strchr(args, ' ');
    if(s == NULL) exit(-1);

    char ip[strlen(args) - strlen(s) + 1];
    memset(ip, 0, strlen(args) - strlen(s) + 1);
    memcpy(ip, args, strlen(args) - strlen(s));

    char port[strlen(s) - 1];
    memset(port, 0, strlen(s) - 1);
    memcpy(port, s + 1, strlen(s) - 1);

    int port_int = atoi(port);

    int sock = create_client_socket(ip, port_int);
    if(sock < 0){
        perror("create_client_socket");
        return;
    }

    printf("Which file would you like to download?\n");

    char *line_n = NULL;
    size_t len = 0;

    getline(&line_n, &len, stdin);

    line_n[strlen(line_n) - 1] = 0;

    int size = 4 + 1 + FILENAME + 2;
    char dlfi[size + 1];
    memset(dlfi, 0, size + 1);

    sprintf(dlfi, "DLFI %s\r\n", fill_with_sharp(line_n, FILENAME));
    if(sendall(sock, dlfi, &size) < 0){
        perror("sendall");
        return;
    }

    char resp[5];
    memset(resp, 0, 5);
    if(recv(sock, resp, 4, 0) != 4){
        perror("recv");
        return;
    }

    if(!strcmp(resp, "FIOK")){
        char c;
        if(recv(sock, &c, 1, 0) != 1){
            perror("recv");
            return;
        }

        int size_size = FILESIZE + 2;
        char sizing[size_size + 1];
        memset(sizing, 0, size_size + 1);

        if(recv(sock, sizing, size_size, 0) != size_size){
            perror("recv");
            return;
        }

        int filesize = atoi(sizing);

        printf("The file %s weight %dB. It will be saved at `downloads/%s`"
        "\n(If a file of the same name already exists, it will be removed.)",
        line_n, filesize, line_n);

        struct stat st = {0};

        if (stat("downloads", &st) == -1) {
            mkdir("downloads", 0777);
        }

        char path[strlen("downloads/") + strlen(line_n) + 1];
        memset(path, 0, strlen("downloads/") + strlen(line_n) + 1);
        sprintf(path, "downloads/%s", line_n);

        int fd = open(path, O_RDWR | O_CREAT, 0777);
        if(fd < 0){
            perror("open");
            return;
        }

        printf("Beggining downloading...\n");

        int buff_size = 8192;

        char buff[buff_size];
        memset(buff, 0, buff_size);
        int len;
        int read = 0;

        while((len = recv(sock, buff, min(buff_size, filesize - read), 0)) > 0){
            read += len;

            if(write(fd, buff, len) < 0){
                perror("write");
                return;
            }

            printf("%f%s done...\n", ((float) read / filesize) * 100, "%");

            memset(buff, 0, buff_size);
        }

        close(fd);

        char endm[7];
        memset(endm, 0, 7);
        if(recv(sock, endm, 6, 0) != 6){
            perror("recv");
            return;
        }

        if(!strcmp(endm,"ENDL\r\n")){
            printf("File downloaded successfully !\n");
        }else{
            printf("Something went wrong while downloading the file...\n");
        }
    }else{
        printf("Unknown file...\n");
    }
}

void help(){
    printf("List of every commands : \n");
    int i = 0;
    while(helps[i] != NULL){
        printf("- %s\n",helps[i]);
        i++;
    }
}