CC=gcc
CFLAGS=-W -Wunused-value -Wall -pthread
LDFLAGS= -lm -g

ALL=diffuseur client

all: $(ALL)

diffuseur: src/C/diffuseur.c src/C/netradio.c
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

client: src/C/client.c src/C/netradio.c
	$(CC) $(CFLAGS) -o $@ $^ $(LDFLAGS)

clean:
	rm -rf $(ALL)

cleano:
	rm -rf src/C/*.o

cleanall: clean cleano