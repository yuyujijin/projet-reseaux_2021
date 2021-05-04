#include "netradio.h"

int create_tcp_server(int port)
{
    int sock = socket(PF_INET, SOCK_STREAM, 0);

    if (setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &(int){1}, sizeof(int)) < 0){
        perror("setsockopt");
        return -1;
    }

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

int create_client_socket(char *adr, int port){
    struct sockaddr_in address_sock;
    address_sock.sin_family = AF_INET;
    address_sock.sin_port = htons(port);
    inet_aton(adr, &address_sock.sin_addr);

    int descr = socket(PF_INET, SOCK_STREAM, 0);
    int r = connect(descr, (struct sockaddr *) &address_sock, sizeof(struct sockaddr_in));
    if(r == -1) return -1;
    return descr;
}

char *fill_with_zeros(int n, int size)
{
    char *s = malloc(sizeof(char) * size + 1);
    if (s == NULL)
        return NULL;
    memset(s, 0, size + 1);

    char nstring[size];
    memset(nstring, 0, size);
    sprintf(nstring, "%d", n);

    memset(s, '0', size - strlen(nstring));
    memcpy(s + (size - strlen(nstring)), nstring, strlen(nstring));

    return s;
}

char *fill_with_sharp(char *s, long unsigned int n)
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

diff_info load_settings(char *filename){
    FILE *fp = fopen(filename, "r");
    if (fp == NULL)
        exit(-1);
    char *line = NULL;
    size_t len = 0;

    diff_info di;
    
    while (getline(&line, &len, fp) != -1)
    {   
        char *c = strchr(line, ':');
        if(c == NULL) exit(-1);

        char key[strlen(line) - strlen(c)];
        memcpy(key, line, strlen(line) - strlen(c));
        char val[strlen(c)];
        memset(val, 0, strlen(c));
        memcpy(val, c + 1, strlen(c) - 1);
        if(val[strlen(val) - 1] == '\n') val[strlen(val) - 1] = 0;

        if(!strcmp(key,"id")){
            di.id = fill_with_sharp(val, ID);
        }else if(!strcmp(key,"port1")){
            di.port1 = atoi(val);
        }else if(!strcmp(key,"port2")){
            di.port2 = atoi(val);
        }else if(!strcmp(key,"ip1")){
            di.ipmulti = strdup(val);
        }
    }

    return di;
}

char *get_host_address(){
    struct hostent *host_entry;
    char hostbuffer[256];

    if(gethostname(hostbuffer, sizeof(hostbuffer)) == -1){
        perror("gethostname"); exit(-1);
    }

    host_entry = gethostbyname(hostbuffer);

    if(host_entry == NULL){
        perror("gethostbyname"); exit(-1);
    }

    return inet_ntoa(*((struct in_addr*) host_entry->h_addr_list[0]));
}

char *normalize_ip(char *ip){
    char *addr = malloc(sizeof(char) * IP);
    if(addr == NULL) return NULL;
    memset(addr, 0, 15);

    char *save;

    char *strToken = strtok_r(strdup(ip),".",&save);
    int i = 0;
    while(strToken != NULL){
        strcat(addr, fill_with_zeros(atoi(strToken), 3));
        if(i++ < 3) strcat(addr, ".");
        
        strToken = strtok_r(NULL,".",&save);
    }

    return addr;
}

// https://beej.us/guide/bgnet/html/#sendrecv
int sendall(int s, char *buf, int *len)
{
    int total = 0;        // how many bytes we've sent
    int bytesleft = *len; // how many we have left to send
    int n; 

    while(total < *len) {
        n = send(s, buf+total, bytesleft, 0);
        if (n == -1) { break; }
        total += n;
        bytesleft -= n;
    }

    *len = total; // return number actually sent here

    return n==-1?-1:0; // return -1 on failure, 0 on success
} 
